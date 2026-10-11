package com.sayana.dojo.exec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.sayana.dojo.config.DojoProperties;

class JShellRunnerTest {

    private final DojoProperties props = new DojoProperties();
    private final JShellRunner runner = new JShellRunner(props, new ExecutionGate(props));

    @Test
    void 標準出力と式の値を返す() {
        RunResult r = runner.run("""
                int x = 3;
                System.out.println("こんにちは " + x);
                x * 2
                """);
        assertThat(r.status()).isEqualTo(RunResult.Status.OK);
        assertThat(r.output()).contains("こんにちは 3");
        assertThat(r.values()).containsExactly("6");
    }

    @Test
    void mainだけのクラスは自動でmainを呼ぶ() {
        RunResult r = runner.run("""
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("Hello, Java!");
                    }
                }
                """);
        assertThat(r.status()).isEqualTo(RunResult.Status.OK);
        assertThat(r.output()).contains("Hello, Java!");
    }

    @Test
    void コンパイルエラーは行番号つきで返す() {
        RunResult r = runner.run("""
                int a = 1;
                String s = 10;
                """);
        assertThat(r.status()).isEqualTo(RunResult.Status.COMPILE_ERROR);
        assertThat(r.errors()).hasSize(1);
        assertThat(r.errors().get(0).line()).isEqualTo(2);
        assertThat(r.errors().get(0).message()).isNotBlank();
    }

    @Test
    void 実行時例外を返す() {
        RunResult r = runner.run("""
                int[] a = new int[2];
                a[5] = 1;
                """);
        assertThat(r.status()).isEqualTo(RunResult.Status.EXCEPTION);
        assertThat(r.exception()).contains("ArrayIndexOutOfBoundsException");
    }

    @Test
    void 無限ループはタイムアウトして本体は固まらない() {
        long start = System.nanoTime();
        RunResult r = runner.run("while (true) { }", Duration.ofSeconds(2));
        assertThat(r.status()).isEqualTo(RunResult.Status.TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(8));
        // 続けて普通に動くこと
        assertThat(runner.run("1 + 1").values()).containsExactly("2");
    }

    @Test
    void 大量の出力は切り詰める() {
        RunResult r = runner.run("for (int i = 0; i < 200000; i++) System.out.println(\"line \" + i);");
        assertThat(r.truncated()).isTrue();
        assertThat(r.output().length()).isLessThanOrEqualTo(props.getExec().getMaxOutputChars() * 3);
    }

    @Test
    void Springを指定するとJShellでもDIコンテナが使える() {
        RunResult r = runner.run("""
                import org.springframework.context.annotation.*;
                class Repo { String find() { return "データ"; } }
                class Service {
                    private final Repo repo;
                    Service(Repo repo) { this.repo = repo; }
                    String run() { return "取得: " + repo.find(); }
                }
                try (var ctx = new AnnotationConfigApplicationContext(Repo.class, Service.class)) {
                    System.out.println(ctx.getBean(Service.class).run());
                }
                """, Duration.ofSeconds(30), java.util.List.of("spring"));
        assertThat(r.status()).as(r.errorText() + r.output()).isEqualTo(RunResult.Status.OK);
        assertThat(r.output()).contains("取得: データ");
        assertThat(r.output()).as("SLF4J の警告は出さない").doesNotContain("SLF4J");
    }

    @Test
    void 知らないライブラリ名はエラーにする() {
        RunResult r = runner.run("1", Duration.ofSeconds(5), java.util.List.of("evil"));
        assertThat(r.status()).isEqualTo(RunResult.Status.ERROR);
    }

    @Test
    void 行頭がドットのメソッドチェーンも1つの文として動く() {
        RunResult r = runner.run("""
                var names = java.util.List.of("ハル", "モカ", "Duke");
                var joined = names.stream()
                        .filter(n -> n.length() == 2)
                        // コメントがあっても大丈夫
                        .sorted()
                        .toList();
                System.out.println(joined);
                int x = "a";
                """);
        assertThat(r.errors()).as("2行目からの文はつながり、エラーは8行目だけ").hasSize(1);
        assertThat(r.errors().get(0).line()).isEqualTo(8);
        RunResult ok = runner.run("""
                var joined = java.util.List.of("b", "a").stream()
                        .sorted()
                        .toList();
                System.out.println(joined);
                """);
        assertThat(ok.output()).contains("[a, b]");
    }

    @Test
    void jshellコマンドと同じくjava_utilなどはimportなしで使える() {
        RunResult r = runner.run("""
                List<String> xs = new ArrayList<>(List.of("b", "a"));
                Collections.sort(xs);
                System.out.println(xs + " " + LocalDate.of(2026, 4, 1).getDayOfWeek());
                """);
        assertThat(r.status()).as(r.errorText()).isEqualTo(RunResult.Status.OK);
        assertThat(r.output()).contains("[a, b] WEDNESDAY");
    }
}
