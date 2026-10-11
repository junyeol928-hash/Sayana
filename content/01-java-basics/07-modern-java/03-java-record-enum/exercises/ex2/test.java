import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyCalcTest {

    @Test
    @DisplayName("コンパクトコンストラクタ:負の金額は作れない(0円はOK)")
    void negativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Money(-1, "JPY"),
                "金額 -1 で IllegalArgumentException が出ていません。コンパクトコンストラクタで amount < 0 をチェックしてください");
        assertDoesNotThrow(() -> new Money(0, "JPY"),
                "金額 0 は正しい値です。amount <= 0 で弾いていないか確認してください");
    }

    @Test
    @DisplayName("コンパクトコンストラクタ:通貨が null や空白なら作れない")
    void invalidCurrency() {
        assertThrows(IllegalArgumentException.class, () -> new Money(100, null),
                "通貨 null で IllegalArgumentException が出ていません");
        assertThrows(IllegalArgumentException.class, () -> new Money(100, "  "),
                "通貨が空白だけのときも IllegalArgumentException にしてください(isBlank を使います)");
    }

    @Test
    @DisplayName("add:同じ通貨なら合計した新しい Money を返し、元の Money は変わらない")
    void addSameCurrency() {
        Money a = new Money(500, "JPY");
        Money result = a.add(new Money(300, "JPY"));
        assertEquals(new Money(800, "JPY"), result,
                "500 JPY + 300 JPY は 800 JPY です");
        assertEquals(new Money(500, "JPY"), a,
                "add を呼んでも元の Money は 500 JPY のままのはずです");
    }

    @Test
    @DisplayName("add:通貨が違えば IllegalArgumentException")
    void addDifferentCurrency() {
        Money yen = new Money(500, "JPY");
        Money dollar = new Money(3, "USD");
        assertThrows(IllegalArgumentException.class, () -> yen.add(dollar),
                "JPY と USD を足そうとしたら IllegalArgumentException を投げてください");
    }

    @Test
    @DisplayName("total:全部足した金額を返す。空リストなら 0")
    void total() {
        List<Money> items = List.of(
                new Money(1200, "JPY"), new Money(800, "JPY"), new Money(55, "JPY"));
        assertEquals(new Money(2055, "JPY"), MoneyCalc.total(items, "JPY"),
                "1200 + 800 + 55 = 2055 JPY です。add の戻り値を受け取り直していますか?");
        assertEquals(new Money(0, "USD"), MoneyCalc.total(List.of(), "USD"),
                "空のリストなら new Money(0, currency) を返してください");
    }

    @Test
    @DisplayName("total:違う通貨が混ざっていたら IllegalArgumentException")
    void totalMixed() {
        List<Money> items = List.of(new Money(1200, "JPY"), new Money(10, "USD"));
        assertThrows(IllegalArgumentException.class, () -> MoneyCalc.total(items, "JPY"),
                "JPY の合計に USD が混ざっていたら IllegalArgumentException にしてください");
    }
}
