package com.sayana.dojo.exec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.sayana.dojo.config.DojoProperties;

class GraderTest {

    private final DojoProperties props = new DojoProperties();
    private final Grader grader = new Grader(props, new ExecutionGate(props));

    private static final String TEST = """
            import static org.junit.jupiter.api.Assertions.*;
            import org.junit.jupiter.api.*;

            class CalcTest {
                @Test
                @DisplayName("2 + 3 は 5")
                void add() {
                    assertEquals(5, Calc.add(2, 3), "add(2, 3) の結果");
                }

                @Test
                @DisplayName("負の数も足せる")
                void negative() {
                    assertEquals(-1, Calc.add(2, -3));
                }
            }
            """;

    @Test
    void 正解なら合格() {
        GradeResult r = grader.grade("Calc", """
                public class Calc {
                    public static int add(int a, int b) { return a + b; }
                }
                """, TEST, Duration.ofSeconds(10));
        assertThat(r.status()).as(r.summaryText() + "\n" + r.output()).isEqualTo(GradeResult.Status.PASSED);
        assertThat(r.tests()).extracting(GradeResult.TestCase::name).contains("2 + 3 は 5", "負の数も足せる");
    }

    @Test
    void 間違いなら失敗理由つきで不合格() {
        GradeResult r = grader.grade("Calc", """
                public class Calc {
                    public static int add(int a, int b) { return a - b; }
                }
                """, TEST, Duration.ofSeconds(10));
        assertThat(r.status()).isEqualTo(GradeResult.Status.FAILED);
        assertThat(r.tests()).filteredOn(t -> !t.passed()).first()
                .satisfies(t -> assertThat(t.message()).contains("expected"));
    }

    @Test
    void コンパイルエラーは日本語の診断を返す() {
        GradeResult r = grader.grade("Calc", """
                public class Calc {
                    public static int add(int a, int b) { return a + b }
                }
                """, TEST, Duration.ofSeconds(10));
        assertThat(r.status()).isEqualTo(GradeResult.Status.COMPILE_ERROR);
        assertThat(r.compileErrors()).first().satisfies(d -> {
            assertThat(d.source()).isEqualTo("あなたのコード");
            assertThat(d.line()).isEqualTo(2);
        });
    }

    @Test
    void 無限ループはタイムアウト() {
        long start = System.nanoTime();
        GradeResult r = grader.grade("Calc", """
                public class Calc {
                    public static int add(int a, int b) { while (true) { } }
                }
                """, TEST, Duration.ofSeconds(3));
        assertThat(r.status()).isEqualTo(GradeResult.Status.TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void メモリを食い潰してもアプリは無事() {
        GradeResult r = grader.grade("Calc", """
                import java.util.*;
                public class Calc {
                    public static int add(int a, int b) {
                        List<long[]> hog = new ArrayList<>();
                        while (true) { hog.add(new long[10_000_000]); }
                    }
                }
                """, TEST, Duration.ofSeconds(10));
        assertThat(r.passed()).isFalse();
    }
}
