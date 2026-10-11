import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ShippingFeeTest {

    // 画面入力やDBから来た文字列を再現するため、リテラルではなく実行時に新しい String を作る
    private static String input(String s) {
        return new String(s);
    }

    @Test
    @DisplayName("GOLD 会員は少額でも送料 0 円")
    void gold() {
        assertEquals(0, ShippingFee.calc(100, input("GOLD")),
                "GOLD 会員は送料 0 円です。rank を == で比較していませんか? 文字列は equals で比較してください");
    }

    @Test
    @DisplayName("SILVER 会員は 2999 円なら 300 円、3000 円ちょうどで無料(境界値)")
    void silverBoundary() {
        assertEquals(300, ShippingFee.calc(2999, input("SILVER")),
                "SILVER 会員で 3000 円未満なら送料は 300 円です");
        assertEquals(0, ShippingFee.calc(3000, input("SILVER")),
                "SILVER 会員は 3000 円「以上」で無料です。>= になっているか確認してください");
    }

    @Test
    @DisplayName("NORMAL 会員は 4999 円なら 500 円、5000 円ちょうどで無料(境界値)")
    void normalBoundary() {
        assertEquals(500, ShippingFee.calc(4999, input("NORMAL")),
                "NORMAL 会員で 5000 円未満なら送料は 500 円です");
        assertEquals(0, ShippingFee.calc(5000, input("NORMAL")),
                "NORMAL 会員は 5000 円「以上」で無料です。>= になっているか確認してください");
    }

    @Test
    @DisplayName("知らないランクは NORMAL と同じ扱い")
    void unknownRank() {
        assertEquals(500, ShippingFee.calc(1000, input("PLATINUM")),
                "知らないランクは NORMAL と同じく 5000 円未満なら 500 円です");
    }

    @Test
    @DisplayName("rank が null でも例外にならず NORMAL と同じ扱い")
    void nullRank() {
        assertDoesNotThrow(() -> ShippingFee.calc(1000, null),
                "rank が null で NullPointerException が出ています。rank.equals(\"GOLD\") ではなく \"GOLD\".equals(rank) の向きで書くか、先に null をはじいてください");
        assertEquals(500, ShippingFee.calc(1000, null),
                "rank が null のときは NORMAL と同じく 5000 円未満なら 500 円です");
    }

    @Test
    @DisplayName("購入金額が 0 円以下なら -1(GOLD 会員でも)")
    void invalidAmount() {
        assertEquals(-1, ShippingFee.calc(0, input("GOLD")),
                "金額 0 円は異常データなので -1 を返します。金額チェック(ガード節)をランク判定より先に書いていますか?");
        assertEquals(-1, ShippingFee.calc(-500, input("NORMAL")),
                "金額がマイナスなら -1 を返してください");
    }
}
