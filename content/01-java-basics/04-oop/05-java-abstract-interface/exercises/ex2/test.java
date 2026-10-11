import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckoutTest {

    // テスト側で用意した「別のルール」。Checkout が PriceRule 型だけを相手にしていれば差し込める
    static class CountingRule implements PriceRule {
        int calls = 0;

        public int apply(int price) {
            calls++;
            return price + 1;
        }
    }

    @Test
    @DisplayName("割引なしなら合計金額そのまま")
    void noDiscount() {
        assertEquals(3000, new Checkout(new NoDiscount()).total(new int[] {1000, 2000}),
                "total() は配列の合計にルールを適用した金額を返します。合計の計算を確認してください");
    }

    @Test
    @DisplayName("10%引きは割引額の1円未満を切り捨て(999円 → 900円)")
    void rateDiscount() {
        assertEquals(2700, new RateDiscount(10).apply(3000),
                "RateDiscount(10) で 3000 円なら 2700 円です");
        assertEquals(900, new RateDiscount(10).apply(999),
                "割引額は price * percent / 100(int の割り算で切り捨て)で 99 円、結果は 900 円です");
    }

    @Test
    @DisplayName("クーポンは引ききれないとき 0 円で止まる(境界値)")
    void couponFloor() {
        assertEquals(0, new CouponDiscount(500).apply(500),
                "500 円の商品に 500 円クーポンなら 0 円です");
        assertEquals(0, new CouponDiscount(500).apply(300),
                "金額がクーポン額より小さいときはマイナスではなく 0 を返してください");
        assertEquals(1, new CouponDiscount(500).apply(501),
                "501 円に 500 円クーポンなら 1 円です");
    }

    @Test
    @DisplayName("ルールは「合計に1回だけ」適用する(商品ごとではない)")
    void applyOnceToSum() {
        CountingRule rule = new CountingRule();
        int result = new Checkout(rule).total(new int[] {100, 200, 300});
        assertEquals(1, rule.calls,
                "rule.apply は合計に対して1回だけ呼んでください。商品ごとに呼んでいませんか?");
        assertEquals(601, result,
                "合計 600 円にルールを1回適用した結果を返してください");
    }

    @Test
    @DisplayName("空のカートは合計 0 円にルールを適用する")
    void emptyCart() {
        assertEquals(0, new Checkout(new CouponDiscount(500)).total(new int[0]),
                "空配列の合計は 0 円です。クーポンを引いても 0 円未満にはなりません");
    }

    @Test
    @DisplayName("レシート文字列にルール名・合計・割引後が入る")
    void receipt() {
        int[] cart = {1000, 2000};
        assertEquals("10%引き: 3000円 → 2700円", new Checkout(new RateDiscount(10)).receipt(cart),
                "receipt() は \"ルール名: 合計円 → 割引後円\" の形式です。RateDiscount の label() も確認してください");
        assertEquals("500円引き: 3000円 → 2500円", new Checkout(new CouponDiscount(500)).receipt(cart),
                "CouponDiscount の label() は \"500円引き\" の形式です");
        assertEquals("通常価格: 3000円 → 3000円", new Checkout(new NoDiscount()).receipt(cart),
                "NoDiscount は default メソッドの label()(\"通常価格\")をそのまま使います");
    }
}
