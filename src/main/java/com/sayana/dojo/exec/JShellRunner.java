package com.sayana.dojo.exec;

import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sayana.dojo.config.DojoProperties;

import jdk.jshell.Diag;
import jdk.jshell.EvalException;
import jdk.jshell.JShell;
import jdk.jshell.JShellException;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.SourceCodeAnalysis.Completeness;
import jdk.jshell.TypeDeclSnippet;
import jdk.jshell.UnresolvedReferenceException;
import jdk.jshell.VarSnippet;

/**
 * サンプルや短いスニペットを JShell API で実行する。
 * <p>
 * JShell はコードを別JVM(リモートエージェント)で実行するので、無限ループやメモリの食い過ぎがあっても
 * アプリ本体は固まらない。タイムアウトしたら実行を止めて JShell ごと捨てる。
 */
@Service
public class JShellRunner {

    private static final Logger log = LoggerFactory.getLogger(JShellRunner.class);
    private static final Pattern MAIN_METHOD = Pattern.compile("static\\s+void\\s+main\\s*\\(");

    /** jshell コマンドの既定の import と同じもの + java.time。import を書かなくても List などが使える。 */
    private static final List<String> DEFAULT_IMPORTS = List.of(
            "java.io.*", "java.math.*", "java.net.*", "java.nio.file.*", "java.util.*",
            "java.util.concurrent.*", "java.util.function.*", "java.util.regex.*", "java.util.stream.*",
            "java.time.*");

    private final DojoProperties.Exec config;
    private final ExecutionGate gate;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "jshell-runner");
        t.setDaemon(true);
        return t;
    });

    public JShellRunner(DojoProperties props, ExecutionGate gate) {
        this.config = props.getExec();
        this.gate = gate;
    }

    public RunResult run(String code) {
        return run(code, config.getTimeout());
    }

    public RunResult run(String code, Duration timeout) {
        return run(code, timeout, List.of());
    }

    /**
     * @param libraries 追加ライブラリ(例: "spring")。JShell のクラスパスに jar を足してから実行する
     */
    public RunResult run(String code, Duration timeout, List<String> libraries) {
        long start = System.nanoTime();
        if (code == null || code.isBlank()) {
            return new RunResult(RunResult.Status.OK, "", List.of(), List.of(), null, 0, false);
        }
        if (!gate.acquire(timeout.toSeconds() + 5)) {
            return new RunResult(RunResult.Status.BUSY, "", List.of(), List.of(),
                    "ほかのコードを実行中です。少し待ってからもう一度どうぞ。", 0, false);
        }
        BoundedOutput out = new BoundedOutput(config.getMaxOutputChars());
        PrintStream ps = new PrintStream(out, true, StandardCharsets.UTF_8);
        JShell jshell = null;
        try {
            jshell = JShell.builder()
                    .out(ps)
                    .err(ps)
                    .in(new ByteArrayInputStream(new byte[0]))
                    .remoteVMOptions("-Xmx" + config.getMaxHeap(), "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1",
                            "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                            "-Duser.language=ja")
                    .build();
            JShell js = jshell;
            for (java.nio.file.Path jar : LibraryClasspath.resolve(libraries)) {
                js.addToClasspath(jar.toString());
            }
            Evaluation evaluation = new Evaluation(code);
            Future<?> future = executor.submit(() -> evaluation.evaluate(js));
            try {
                future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                stopQuietly(js);
                future.cancel(true);
                return result(RunResult.Status.TIMEOUT, out, evaluation,
                        "実行が " + timeout.toSeconds() + " 秒を超えたので止めました(無限ループになっていませんか?)", start);
            } catch (ExecutionException e) {
                log.warn("JShell 実行中の内部エラー", e.getCause());
                return result(RunResult.Status.ERROR, out, evaluation, "内部エラー: " + e.getCause(), start);
            }
            RunResult.Status status = !evaluation.errors.isEmpty() ? RunResult.Status.COMPILE_ERROR
                    : evaluation.exception != null ? RunResult.Status.EXCEPTION
                    : RunResult.Status.OK;
            return result(status, out, evaluation, evaluation.exception, start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RunResult(RunResult.Status.ERROR, out.text(), List.of(), List.of(), "中断されました", 0, false);
        } catch (IllegalArgumentException e) {
            return new RunResult(RunResult.Status.ERROR, out.text(), List.of(), List.of(), e.getMessage(), 0, false);
        } catch (RuntimeException e) {
            log.warn("JShell を起動できませんでした", e);
            return new RunResult(RunResult.Status.ERROR, out.text(), List.of(), List.of(),
                    "JShell を起動できませんでした: " + e.getMessage(), 0, false);
        } finally {
            if (jshell != null) {
                closeQuietly(jshell);
            }
            gate.release();
        }
    }

    private static RunResult result(RunResult.Status status, BoundedOutput out, Evaluation ev, String exception,
                                    long start) {
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        return new RunResult(status, out.text(), List.copyOf(ev.values), List.copyOf(ev.errors), exception, elapsed,
                out.truncated());
    }

    private static void stopQuietly(JShell js) {
        try {
            js.stop();
        } catch (RuntimeException e) {
            log.debug("JShell stop に失敗", e);
        }
    }

    private void closeQuietly(JShell js) {
        // close() は止まったリモートJVMの終了を待つことがあるので、別スレッドで投げっぱなしにする
        executor.submit(() -> {
            try {
                js.close();
            } catch (RuntimeException e) {
                log.debug("JShell close に失敗", e);
            }
        });
    }

    /** 1回分の評価。スニペットに分割して順に eval する。 */
    private static final class Evaluation {
        private final String code;
        private final List<String> values = new ArrayList<>();
        private final List<CodeDiagnostic> errors = new ArrayList<>();
        private volatile String exception;

        Evaluation(String code) {
            this.code = joinLeadingDots(code.replace("\r\n", "\n"));
        }

        void evaluate(JShell js) {
            for (String pkg : DEFAULT_IMPORTS) {
                js.eval("import " + pkg + ";");
            }
            SourceCodeAnalysis sca = js.sourceCodeAnalysis();
            String remaining = code;
            boolean executedStatement = false;
            List<String> mainCandidates = new ArrayList<>();
            while (!remaining.isBlank()) {
                int offset = code.length() - remaining.length() + leadingWhitespace(remaining);
                SourceCodeAnalysis.CompletionInfo info = sca.analyzeCompletion(remaining);
                String source;
                if (info.completeness().isComplete()) {
                    source = info.source();
                    remaining = info.remaining();
                } else if (info.completeness() == Completeness.EMPTY) {
                    break;
                } else {
                    // 途中で終わっている・構文が壊れている → まとめて eval してコンパイルエラーを出させる
                    source = remaining;
                    remaining = "";
                }
                for (SnippetEvent ev : js.eval(source)) {
                    if (ev.causeSnippet() != null) {
                        continue;
                    }
                    Snippet snippet = ev.snippet();
                    if (ev.status() == Snippet.Status.REJECTED) {
                        js.diagnostics(snippet).forEach(d -> errors.add(toDiagnostic(d, source, offset)));
                        if (errors.isEmpty()) {
                            errors.add(new CodeDiagnostic("コード", lineOf(offset), 0, "このコードは解釈できませんでした", ""));
                        }
                        return;
                    }
                    if (ev.exception() != null) {
                        exception = describe(ev.exception(), js);
                        return;
                    }
                    if (snippet.kind() == Snippet.Kind.EXPRESSION
                            || snippet.kind() == Snippet.Kind.STATEMENT) {
                        executedStatement = true;
                    }
                    if (snippet instanceof VarSnippet v && v.subKind() == Snippet.SubKind.TEMP_VAR_EXPRESSION_SUBKIND
                            && ev.value() != null && !"void".equals(v.typeName())) {
                        values.add(ev.value());
                    } else if (snippet.kind() == Snippet.Kind.EXPRESSION && ev.value() != null) {
                        values.add(ev.value());
                    }
                    if (snippet.kind() == Snippet.Kind.VAR) {
                        executedStatement = true;
                    }
                    if (snippet instanceof TypeDeclSnippet t && MAIN_METHOD.matcher(t.source()).find()) {
                        mainCandidates.add(t.name());
                    }
                }
            }
            // main を持つクラスを宣言しただけのコードなら、main を呼んであげる
            if (!executedStatement && mainCandidates.size() == 1) {
                for (SnippetEvent ev : js.eval(mainCandidates.get(0) + ".main(new String[0]);")) {
                    if (ev.exception() != null) {
                        exception = describe(ev.exception(), js);
                    } else if (ev.status() == Snippet.Status.REJECTED) {
                        js.diagnostics(ev.snippet()).forEach(d -> errors.add(
                                new CodeDiagnostic("main の呼び出し", 0, 0, d.getMessage(Locale.JAPANESE), "")));
                    }
                }
            }
        }

        private CodeDiagnostic toDiagnostic(Diag d, String source, int offset) {
            long pos = Math.max(0, d.getStartPosition());
            int lead = leadingWhitespace(source);
            int end = (int) Math.min(Math.max(pos, lead), source.length());
            long line = lineOf(offset) + countNewlines(source.substring(lead), end - lead);
            String codeLine = line > 0 ? lineText(code, (int) line) : "";
            return new CodeDiagnostic("コード", line, 0, d.getMessage(Locale.JAPANESE), codeLine);
        }

        private long lineOf(int offset) {
            return 1 + countNewlines(code, Math.min(offset, code.length()));
        }
    }

    private static String describe(JShellException e, JShell js) {
        StringBuilder sb = new StringBuilder();
        if (e instanceof EvalException ee) {
            sb.append("例外が発生しました: ").append(ee.getExceptionClassName());
            if (ee.getMessage() != null) {
                sb.append(": ").append(ee.getMessage());
            }
        } else if (e instanceof UnresolvedReferenceException ue) {
            sb.append("まだ定義されていないものを使っています: ").append(ue.getSnippet().source().lines().findFirst().orElse(""));
            js.unresolvedDependencies(ue.getSnippet()).forEach(dep -> sb.append("\n  未定義: ").append(dep));
            return sb.toString();
        } else {
            sb.append(e.getMessage());
        }
        StackTraceElement[] trace = e.getStackTrace();
        int shown = 0;
        for (StackTraceElement el : trace) {
            if (el.getClassName().startsWith("jdk.") || el.getClassName().startsWith("java.lang.reflect")
                    || el.getClassName().startsWith("jdk.internal")) {
                continue;
            }
            sb.append("\n    at ").append(simplify(el));
            if (++shown >= 6) {
                break;
            }
        }
        return sb.toString();
    }

    private static String simplify(StackTraceElement el) {
        String cls = el.getClassName().replaceAll("REPL\\.\\$JShell\\$\\d+[A-Z]?\\$?", "").replaceAll("^\\.", "");
        String method = el.getMethodName();
        String where = cls.isEmpty() || cls.startsWith("do_it$") ? method : cls + "." + method;
        return where + (el.getLineNumber() > 0 ? "(" + el.getLineNumber() + "行目付近)" : "");
    }

    private static final Pattern LEADING_DOT = Pattern.compile("^(\\s*)\\.(?=[A-Za-z_<])");

    /**
     * 行頭がドットで始まるメソッドチェーン(Stream や Builder でよく書く形)をつなぐ。
     * JShell は「list.stream()」で行が終わると文が完結したとみなすので、
     * 行頭の「.」を前の行の末尾へ移す(改行の数は変えないので行番号はずれない)。
     */
    static String joinLeadingDots(String code) {
        String[] lines = code.split("\n", -1);
        boolean inTextBlock = false;
        boolean inComment = false;
        int prev = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!inTextBlock && !inComment && prev >= 0) {
                Matcher m = LEADING_DOT.matcher(line);
                if (m.find() && !lines[prev].stripTrailing().endsWith(";") && !lines[prev].contains("//")) {
                    lines[prev] = lines[prev].stripTrailing() + ".";
                    lines[i] = m.group(1) + line.substring(m.end());
                    line = lines[i];
                }
            }
            if (countOccurrences(line, "\"\"\"") % 2 == 1) {
                inTextBlock = !inTextBlock;
            }
            if (!inTextBlock) {
                if (line.contains("/*") && !line.contains("*/")) {
                    inComment = true;
                } else if (inComment && line.contains("*/")) {
                    inComment = false;
                }
            }
            if (!line.isBlank() && !inComment) {
                prev = i;
            }
        }
        return String.join("\n", lines);
    }

    private static int countOccurrences(String s, String token) {
        int n = 0;
        for (int i = s.indexOf(token); i >= 0; i = s.indexOf(token, i + token.length())) {
            n++;
        }
        return n;
    }

    private static int leadingWhitespace(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int countNewlines(String s, int end) {
        int n = 0;
        for (int i = 0; i < end; i++) {
            if (s.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    static String lineText(String code, int line) {
        String[] lines = code.split("\n", -1);
        return line >= 1 && line <= lines.length ? lines[line - 1] : "";
    }
}
