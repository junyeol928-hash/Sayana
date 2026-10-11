import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrafficCalculatorTest {

    @Test
    @DisplayName("小さな値:1000バイト × 100回 × 1日 = 100000")
    void small() {
        assertEquals(100_000L, TrafficCalculator.totalBytes(1_000, 100, 1),
                "1000 × 100 × 1 = 100000 になるはずです。3つの引数を掛け算していますか?");
    }

    @Test
    @DisplayName("最初の掛け算で int を超える:50000 × 100000 × 1 = 50億")
    void overflowAtFirstMultiply() {
        assertEquals(5_000_000_000L, TrafficCalculator.totalBytes(50_000, 100_000, 1),
                "50000 × 100000 = 50億 になるはずです。最初の掛け算が int のまま計算されて溢れています。"
                        + "掛け算を始める前に、1つ目の値を (long) でキャストしましょう。");
    }

    @Test
    @DisplayName("2つ目の掛け算で int を超える:2000000 × 1000 × 30 = 600億")
    void overflowAtSecondMultiply() {
        assertEquals(60_000_000_000L, TrafficCalculator.totalBytes(2_000_000, 1_000, 30),
                "2000000 × 1000 × 30 = 600億 になるはずです。計算全体を (long)(...) で囲んでも、カッコの中で先に溢れます。"
                        + "キャストは最初の掛け算の前に付けましょう。");
    }

    @Test
    @DisplayName("境界値:Integer.MAX_VALUE × 2 × 1 = 4294967294")
    void intMaxValue() {
        assertEquals(4_294_967_294L, TrafficCalculator.totalBytes(Integer.MAX_VALUE, 2, 1),
                "int の最大値 2147483647 の2倍、4294967294 になるはずです。int の範囲を超える計算は long で行いましょう。");
    }

    @Test
    @DisplayName("リクエストが0件 → 0")
    void zero() {
        assertEquals(0L, TrafficCalculator.totalBytes(0, 100_000, 30),
                "平均サイズが 0 なら総転送量も 0 になるはずです。");
    }
}
