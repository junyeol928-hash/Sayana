import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvoiceTest {

    private static final List<Item> ITEMS = List.of(
            new Item("ペン", 120, 3),
            new Item("ノート", 250, 2));

    @Test
    @DisplayName("build の出力はリファクタリング前と1文字も変わらない(会員・非会員)")
    void buildUnchanged() {
        assertEquals("ペン x3 = 360\nノート x2 = 500\n小計: 860\n割引: -86\n税: 77\n合計: 851",
                Invoice.build(ITEMS, true),
                "会員の請求書の文字列が変わってしまいました。分割したメソッドの戻り値や、build からの呼び出し方を確認してください");
        assertEquals("ペン x3 = 360\nノート x2 = 500\n小計: 860\n割引: -0\n税: 86\n合計: 946",
                Invoice.build(ITEMS, false),
                "非会員の請求書の文字列が変わってしまいました");
    }

    @Test
    @DisplayName("subtotal:単価 × 数量 の合計(空なら 0)")
    void subtotal() {
        assertEquals(860, Invoice.subtotal(ITEMS),
                "subtotal は 120×3 + 250×2 = 860 を返すはずです。build の「小計」の処理を移しましたか?");
        assertEquals(0, Invoice.subtotal(List.of()), "明細が空なら小計は 0 です");
    }

    @Test
    @DisplayName("discount:会員は10%、非会員は小計1万円以上で5%(境界値)")
    void discount() {
        assertEquals(1234, Invoice.discount(12345, true),
                "会員の割引は小計の10%(端数切り捨て)です");
        assertEquals(0, Invoice.discount(9999, false),
                "非会員で小計 9999 円なら割引はありません");
        assertEquals(500, Invoice.discount(10000, false),
                "非会員でも小計 10000 円ちょうどなら5%(500円)割引です");
    }

    @Test
    @DisplayName("tax:割引後の金額の10%(端数切り捨て)")
    void tax() {
        assertEquals(900, Invoice.tax(9000), "9000 円の消費税は 900 円です");
        assertEquals(123, Invoice.tax(1239), "消費税の端数は切り捨てです(1239 円 → 123 円)");
    }

    @Test
    @DisplayName("line:明細1行の文字列")
    void line() {
        assertEquals("ペン x3 = 360", Invoice.line(new Item("ペン", 120, 3)),
                "明細1行は「商品名 x数量 = 金額」の形式です。build の中の式をそのまま移してください");
    }
}
