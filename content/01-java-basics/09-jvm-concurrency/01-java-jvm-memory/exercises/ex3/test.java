import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SumCalculatorTest {

    @Test
    @DisplayName("sumTo:小さい n で正しい合計を返す")
    void sumToSmall() {
        assertEquals(55L, SumCalculator.sumTo(10), "1〜10 の合計は 55 です");
        assertEquals(1L, SumCalculator.sumTo(1), "1 までの合計は 1 です");
    }

    @Test
    @DisplayName("sumTo:n が 0 以下なら 0(境界値)")
    void sumToZeroOrNegative() {
        assertEquals(0L, SumCalculator.sumTo(0), "n = 0 なら 0 を返してください");
        assertEquals(0L, SumCalculator.sumTo(-5), "n がマイナスなら 0 を返してください");
    }

    @Test
    @DisplayName("sumTo:n = 100万でも StackOverflowError にならない")
    void sumToLarge() {
        long result = assertDoesNotThrow(() -> SumCalculator.sumTo(1_000_000),
                "n = 100万で例外が出ました。再帰のままだとフレームが100万個積まれて StackOverflowError になります。for 文で書き直してください");
        assertEquals(500_000_500_000L, result,
                "1〜100万の合計は 500000500000 です。int ではなく long に足していますか?");
    }

    @Test
    @DisplayName("sumOf:配列の合計を返す。空なら 0")
    void sumOfSmall() {
        assertEquals(12L, SumCalculator.sumOf(new int[]{3, 4, 5}), "3 + 4 + 5 = 12 です");
        assertEquals(0L, SumCalculator.sumOf(new int[0]), "空の配列の合計は 0 です");
    }

    @Test
    @DisplayName("sumOf:要素が100万個でも StackOverflowError にならない")
    void sumOfLarge() {
        int[] values = new int[1_000_000];
        Arrays.fill(values, 2);
        long result = assertDoesNotThrow(() -> SumCalculator.sumOf(values),
                "要素100万個で例外が出ました。sumFrom の再帰をやめて、for 文で全要素を足してください");
        assertEquals(2_000_000L, result, "2 が100万個なら合計は 2000000 です");
    }
}
