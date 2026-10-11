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
}
