import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductTest {

    @Test
    @DisplayName("コンストラクタで3つのフィールドに値が入る")
    void constructorSetsFields() {
        Product p = new Product("ノート", 250, 4);
        assertEquals("ノート", p.name, "name が設定されていません。コンストラクタで this.name = name; と書いているか確認してください");
        assertEquals(250, p.price, "price が設定されていません。this.price = price; になっているか確認してください");
        assertEquals(4, p.quantity, "quantity が設定されていません。this.quantity = quantity; になっているか確認してください");
    }

    @Test
    @DisplayName("小計は 単価 × 数量(120円 × 3個 = 360円)")
    void subtotal() {
        Product p = new Product("ペン", 120, 3);
        assertEquals(360, p.subtotal(), "120円 × 3個 の小計は 360 になるはずです");
    }

    @Test
    @DisplayName("数量0個なら小計は0円(境界値)")
    void zeroQuantity() {
        Product p = new Product("ペン", 120, 0);
        assertEquals(0, p.subtotal(), "数量0なら小計は0になるはずです");
    }

    @Test
    @DisplayName("add で数量が増え、小計も変わる。他のインスタンスには影響しない")
    void addQuantity() {
        Product pen = new Product("ペン", 120, 3);
        Product note = new Product("ノート", 250, 1);
        pen.add(2);
        assertEquals(5, pen.quantity, "add(2) のあと数量は 3 + 2 = 5 になるはずです");
        assertEquals(600, pen.subtotal(), "add(2) のあとの小計は 120 × 5 = 600 になるはずです");
        assertEquals(1, note.quantity, "別の商品の数量が変わっています。static を付けていないか確認してください");
    }

    @Test
    @DisplayName("toString は「ペン 120円 x 3個 = 360円」の形式")
    void toStringFormat() {
        assertEquals("ペン 120円 x 3個 = 360円", new Product("ペン", 120, 3).toString(),
                "toString の形式が違います。スペース・「円」「x」「個」「=」の位置を問題文と見比べてください");
        assertEquals("消しゴム 80円 x 10個 = 800円", new Product("消しゴム", 80, 10).toString(),
                "名前・単価・数量はフィールドの値を使ってください(固定の文字列になっていませんか?)");
    }
}
