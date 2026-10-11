import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaxCalculatorTest {

    @Test
    @DisplayName("1000円・税率10% → 1100円")
    void basic() {
        assertEquals(1100, TaxCalculator.priceWithTax(1000, 0.10),
                "1000円・税率10% は 1100円 になるはずです。price * (1 + taxRate) を計算していますか?");
    }

    @Test
    @DisplayName("1980円・税率10% → 2178円")
    void price1980() {
        assertEquals(2178, TaxCalculator.priceWithTax(1980, 0.10),
                "1980円・税率10% は 2178円 になるはずです。");
    }

    @Test
    @DisplayName("98円・軽減税率8% → 105円(105.84 を切り捨て)")
    void reducedRate() {
        assertEquals(105, TaxCalculator.priceWithTax(98, 0.08),
                "98 × 1.08 = 105.84 なので、切り捨てて 105円 になるはずです。四捨五入(106)になっていませんか? taxRate を使わず 0.10 を直接書いていませんか?");
    }

    @Test
    @DisplayName("999円・税率10% → 1098円(1098.9 を四捨五入しない)")
    void floorNotRound() {
        assertEquals(1098, TaxCalculator.priceWithTax(999, 0.10),
                "999 × 1.10 = 1098.9 なので、1円未満を切り捨てて 1098円 になるはずです。Math.round ではなく Math.floor を使いましょう。");
    }

    @Test
    @DisplayName("0円 → 0円")
    void zero() {
        assertEquals(0, TaxCalculator.priceWithTax(0, 0.10),
                "0円の商品は税込でも 0円 になるはずです。");
    }

    @Test
    @DisplayName("1円・軽減税率8% → 1円(1.08 を切り捨て)")
    void oneYen() {
        assertEquals(1, TaxCalculator.priceWithTax(1, 0.08),
                "1 × 1.08 = 1.08 なので、切り捨てて 1円 になるはずです。");
    }
}
