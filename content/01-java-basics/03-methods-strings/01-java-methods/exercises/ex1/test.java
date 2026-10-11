import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvoiceCalcTest {

    @Test
    @DisplayName("小計は 単価 × 数量(数量 0 なら 0 円)")
    void subtotal() {
        assertEquals(2940, InvoiceCalc.subtotal(980, 3),
                "subtotal(980, 3) は 2940 です。単価 × 数量 を return していますか?");
        assertEquals(0, InvoiceCalc.subtotal(980, 0),
                "数量 0 なら小計は 0 円です");
    }

    @Test
    @DisplayName("消費税は 10%、1円未満は切り捨て")
    void tax() {
        assertEquals(294, InvoiceCalc.tax(2940), "tax(2940) は 294 です");
        assertEquals(99, InvoiceCalc.tax(999),
                "tax(999) は 99.9 を切り捨てて 99 です。double を使わず int のまま amount * 10 / 100 で計算しましょう");
        assertEquals(0, InvoiceCalc.tax(9), "9 円の消費税は 0.9 → 切り捨てで 0 円です");
    }

    @Test
    @DisplayName("値引きなしの total は 小計 + 消費税")
    void totalWithoutDiscount() {
        assertEquals(3234, InvoiceCalc.total(980, 3),
                "total(980, 3) は 2940 + 294 = 3234 です。subtotal と tax を呼び出して足していますか?");
    }

    @Test
    @DisplayName("値引きありの total は、値引き後の金額に消費税を足す")
    void totalWithDiscount() {
        assertEquals(2750, InvoiceCalc.total(980, 3, 440),
                "total(980, 3, 440) は (2940 - 440) = 2500 に消費税 250 を足して 2750 です。税は値引き「後」の金額にかけます");
        assertEquals(3234, InvoiceCalc.total(980, 3, 0),
                "値引き 0 円なら値引きなしの total と同じ 3234 になるはずです");
    }

    @Test
    @DisplayName("値引きが小計以上なら 0 円(マイナスにしない・境界値)")
    void discountExceedsSubtotal() {
        assertEquals(0, InvoiceCalc.total(100, 1, 500),
                "値引きが小計を超えたら 0 円です。マイナスの請求額になっていませんか?");
        assertEquals(0, InvoiceCalc.total(100, 1, 100),
                "値引きが小計とちょうど同じなら 0 円です");
        assertEquals(1, InvoiceCalc.total(100, 1, 99),
                "小計 100 円・値引き 99 円なら 1 円(税は 0.1 → 0 円)です");
    }
}
