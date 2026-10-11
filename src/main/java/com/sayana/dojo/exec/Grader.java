package com.sayana.dojo.exec;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.platform.console.ConsoleLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sayana.dojo.config.DojoProperties;
import com.sayana.dojo.content.ExerciseDef;

/**
 * 演習の採点。
 * <ol>
 *   <li>提出コード・採点テスト・採点ランナーを一時ディレクトリに書き出す</li>
 *   <li>javac(javax.tools)でコンパイルし、エラーは日本語メッセージで返す</li>
 *   <li>別JVMプロセスで JUnit を実行。タイムアウトとヒープ上限つき</li>
 * </ol>
 */
@Service
public class Grader {

    private static final Logger log = LoggerFactory.getLogger(Grader.class);
    private static final String RUNNER_CLASS = "DojoTestRunner";

    private final DojoProperties.Exec config;
    private final ExecutionGate gate;
    private volatile Path junitJar;
    private volatile Path runnerClasses;

    public Grader(DojoProperties props, ExecutionGate gate) {
        this.config = props.getExec();
        this.gate = gate;
    }

    /** Spring を使う演習は JVM の起動と DI コンテナの準備に時間がかかるので、最低これだけ待つ。 */
    private static final Duration SPRING_MIN_TIMEOUT = Duration.ofSeconds(20);

    public GradeResult grade(ExerciseDef exercise, String code) {
        Duration timeout = config.getTimeout();
        if (exercise.usesSpring() && timeout.compareTo(SPRING_MIN_TIMEOUT) < 0) {
            timeout = SPRING_MIN_TIMEOUT;
        }
        return grade(exercise.className(), code, exercise.test(), exercise.libraries(), timeout);
    }

    public GradeResult grade(String className, String code, String testCode, Duration timeout) {
        return grade(className, code, testCode, List.of(), timeout);
    }

    /**
     * @param libraries 追加ライブラリ(例: "spring")。アプリ自身のクラスパスから jar を探して渡す
     */
    public GradeResult grade(String className, String code, String testCode, List<String> libraries,
                             Duration timeout) {
        long start = System.nanoTime();
        if (!gate.acquire(timeout.toSeconds() + 5)) {
            return error("ほかのコードを実行中です。少し待ってからもう一度どうぞ。", start);
        }
        Path work = null;
        try {
            work = Files.createTempDirectory("dojo-grade-");
            Path src = Files.createDirectories(work.resolve("src"));
            Path classes = Files.createDirectories(work.resolve("classes"));
            Path userFile = src.resolve(className + ".java");
            Path testFile = src.resolve(className + "Test.java");
            String normalizedCode = code == null ? "" : code.replace("\r\n", "\n");
            Files.writeString(userFile, normalizedCode, StandardCharsets.UTF_8);
            Files.writeString(testFile, testCode, StandardCharsets.UTF_8);

            List<Path> extraJars = LibraryClasspath.resolve(libraries);
            List<CodeDiagnostic> errors = compile(List.of(userFile, testFile), classes, normalizedCode, className,
                    extraJars);
            if (!errors.isEmpty()) {
                boolean onlyTest = errors.stream().allMatch(e -> e.source().equals("採点テスト"));
                String message = onlyTest
                        ? "あなたのコードはコンパイルできましたが、採点テストが期待する形(クラス名・メソッド名・引数・戻り値の型)と合っていません。問題文のシグネチャと見比べてください。"
                        : "コンパイルエラーです。行番号とメッセージを手がかりに直してみましょう。";
                return new GradeResult(GradeResult.Status.COMPILE_ERROR, List.of(), errors, message, "",
                        elapsed(start));
            }
            return runTests(work, classes, className, extraJars, timeout, start);
        } catch (IOException e) {
            log.warn("採点の準備に失敗しました", e);
            return error("採点の準備に失敗しました: " + e.getMessage(), start);
        } finally {
            gate.release();
            deleteQuietly(work);
        }
    }

    private List<CodeDiagnostic> compile(List<Path> files, Path classes, String userCode, String className,
                                         List<Path> extraJars) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("javac が見つかりません。JRE ではなく JDK 21 で起動してください。");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, Locale.JAPANESE,
                StandardCharsets.UTF_8)) {
            // -parameters: Spring が引数名(@PathVariable など)を読めるようにする
            List<String> options = List.of("-encoding", "UTF-8", "-proc:none", "-g", "-parameters", "-Xlint:none",
                    "-d", classes.toString(), "-cp", classpath(extraJars, List.of(junitJar())));
            Boolean ok = compiler.getTask(null, fm, diagnostics, options, null, fm.getJavaFileObjectsFromPaths(files))
                    .call();
            List<CodeDiagnostic> errors = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() != Diagnostic.Kind.ERROR) {
                    continue;
                }
                String file = d.getSource() == null ? "" : Path.of(d.getSource().toUri()).getFileName().toString();
                boolean user = file.equals(className + ".java");
                String codeLine = user ? JShellRunner.lineText(userCode, (int) d.getLineNumber()) : "";
                errors.add(new CodeDiagnostic(user ? "あなたのコード" : "採点テスト", d.getLineNumber(),
                        d.getColumnNumber(), d.getMessage(Locale.JAPANESE), codeLine));
            }
            if (!Boolean.TRUE.equals(ok) && errors.isEmpty()) {
                errors.add(new CodeDiagnostic("あなたのコード", 0, 0, "コンパイルに失敗しました", ""));
            }
            return errors;
        }
    }

    private GradeResult runTests(Path work, Path classes, String className, List<Path> extraJars, Duration timeout,
                                 long start) throws IOException {
        Path resultFile = work.resolve("result.txt");
        String javaExe = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();
        // 追加ライブラリは JUnit の一体型 jar より前に置く(同名クラスがあるときに新しい方を使うため)
        List<Path> head = new ArrayList<>(List.of(classes, runnerClasses()));
        head.addAll(extraJars);
        String cp = classpath(head, List.of(junitJar()));
        ProcessBuilder pb = new ProcessBuilder(javaExe,
                "-Xmx" + config.getMaxHeap(), "-Xss1m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1",
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-Duser.language=ja", "-Djava.awt.headless=true", "-Dslf4j.internal.verbosity=ERROR", "-Dorg.jboss.logging.provider=slf4j",
                "-cp", cp, RUNNER_CLASS, className + "Test", resultFile.toString(), className)
                .directory(work.toFile());
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        pb.environment().remove("ANTHROPIC_API_KEY");
        Process process = pb.start();
        process.getOutputStream().close();
        BoundedOutput output = new BoundedOutput(config.getMaxOutputChars());
        Thread outPump = pump(process.getInputStream(), output);
        Thread errPump = pump(process.getErrorStream(), output);
        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        joinQuietly(outPump);
        joinQuietly(errPump);

        List<GradeResult.TestCase> tests = new ArrayList<>();
        List<String> containerFailures = new ArrayList<>();
        boolean ended = false;
        if (Files.exists(resultFile)) {
            for (String line : Files.readAllLines(resultFile, StandardCharsets.UTF_8)) {
                if (line.equals("END")) {
                    ended = true;
                    continue;
                }
                String[] f = line.split("\t", -1);
                if (f.length < 4 || !f[0].equals("TEST")) {
                    continue;
                }
                String name = decode(f[2]);
                String message = decode(f[3]);
                switch (f[1]) {
                    case "SUCCESSFUL" -> tests.add(new GradeResult.TestCase(name, true, ""));
                    case "CONTAINER_FAILED" -> containerFailures.add(message);
                    case "SKIPPED" -> { }
                    default -> tests.add(new GradeResult.TestCase(name, false, message));
                }
            }
        }
        String out = output.text();
        if (!finished) {
            return new GradeResult(GradeResult.Status.TIMEOUT, tests, List.of(),
                    "実行が " + timeout.toSeconds() + " 秒を超えたので止めました。無限ループになっていませんか?(ループの終了条件を確認)",
                    out, elapsed(start));
        }
        if (!ended) {
            String why = out.contains("OutOfMemoryError") ? "メモリ不足(OutOfMemoryError)で止まりました。"
                    : out.contains("StackOverflowError") ? "スタックオーバーフローで止まりました(再帰が止まっていない?)。"
                    : "テストが最後まで実行されませんでした(System.exit を呼んでいませんか?)。";
            return new GradeResult(GradeResult.Status.ERROR, tests, List.of(), why, out, elapsed(start));
        }
        if (!containerFailures.isEmpty()) {
            tests.add(new GradeResult.TestCase("テストの準備", false, String.join("\n", containerFailures)));
        }
        if (tests.isEmpty()) {
            return new GradeResult(GradeResult.Status.ERROR, tests, List.of(), "テストが1件も実行されませんでした。", out,
                    elapsed(start));
        }
        boolean allPassed = tests.stream().allMatch(GradeResult.TestCase::passed);
        return new GradeResult(allPassed ? GradeResult.Status.PASSED : GradeResult.Status.FAILED, tests, List.of(),
                allPassed ? "全テスト合格!" : "不合格のテストがあります。メッセージの「期待値」と「実際の値」を見比べましょう。",
                out, elapsed(start));
    }

    /** 採点用の JUnit(console-standalone)jar の場所。 */
    Path junitJar() {
        Path jar = junitJar;
        if (jar == null) {
            synchronized (this) {
                if (junitJar == null) {
                    junitJar = locateJunitJar();
                }
                jar = junitJar;
            }
        }
        return jar;
    }

    private static Path locateJunitJar() {
        try {
            var location = ConsoleLauncher.class.getProtectionDomain().getCodeSource().getLocation();
            if ("file".equals(location.getProtocol())) {
                Path p = Path.of(location.toURI());
                if (Files.isRegularFile(p)) {
                    return p;
                }
            }
            // 実行可能jar(java -jar)の中に入っている場合は一時ファイルに取り出す
            try (InputStream in = location.openStream()) {
                Path tmp = Files.createTempFile("junit-platform-console-standalone", ".jar");
                tmp.toFile().deleteOnExit();
                Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return tmp;
            }
        } catch (IOException | URISyntaxException | RuntimeException e) {
            throw new IllegalStateException("junit-platform-console-standalone の jar が見つかりません", e);
        }
    }

    /** 採点ランナーは最初の1回だけコンパイルして使い回す。 */
    private Path runnerClasses() throws IOException {
        Path dir = runnerClasses;
        if (dir == null) {
            synchronized (this) {
                if (runnerClasses == null) {
                    Path tmp = Files.createTempDirectory("dojo-runner-");
                    Path src = tmp.resolve(RUNNER_CLASS + ".java");
                    try (InputStream in = getClass().getResourceAsStream("/grader/" + RUNNER_CLASS + ".java")) {
                        if (in == null) {
                            throw new IllegalStateException("grader/" + RUNNER_CLASS + ".java がありません");
                        }
                        Files.copy(in, src);
                    }
                    List<CodeDiagnostic> errors = compile(List.of(src), tmp, "", RUNNER_CLASS, List.of());
                    if (!errors.isEmpty()) {
                        throw new IllegalStateException("採点ランナーをコンパイルできません: " + errors);
                    }
                    runnerClasses = tmp;
                }
                dir = runnerClasses;
            }
        }
        return dir;
    }

    private static String classpath(List<Path> first, List<Path> extra) {
        List<String> entries = new ArrayList<>();
        first.forEach(p -> entries.add(p.toString()));
        extra.forEach(p -> entries.add(p.toString()));
        return String.join(java.io.File.pathSeparator, entries);
    }

    private static Thread pump(InputStream in, BoundedOutput sink) {
        Thread t = new Thread(() -> {
            try (in) {
                in.transferTo(sink);
            } catch (IOException ignored) {
                // プロセスが殺されたときは閉じられる
            }
        }, "grader-pump");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void joinQuietly(Thread t) {
        try {
            t.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String decode(String b64) {
        return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    private static GradeResult error(String message, long start) {
        return new GradeResult(GradeResult.Status.ERROR, List.of(), List.of(), message, "", elapsed(start));
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            log.debug("一時ディレクトリを削除できませんでした: {}", dir, e);
        }
    }
}
