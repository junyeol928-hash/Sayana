import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductTest {

    @Test
    @DisplayName("toString() は \"[コード] 商品名 価格円\"")
    void productToString() {
        assertEquals("[A001] りんご 120円", new Product("A001", "りんご", 120).toString(),
                "toString() は \"[A001] りんご 120円\" の形式です。括弧や空白の位置を確認してください");
    }

    @Test
    @DisplayName("商品コードが同じなら、名前や価格が違っても equals は true")
    void equalsSameCode() {
        Product a = new Product("A001", "りんご", 120);
        Object b = new Product(new String("A001"), "林檎", 150);
        assertTrue(a.equals(b),
                "コードが同じなら true です。equals の引数が Object 型か、コードを == ではなく equals で比べているか確認してください");
        assertEquals(a.hashCode(), b.hashCode(),
                "equals が true になる2つの商品は、hashCode も同じ値でなければなりません");
    }

    @Test
    @DisplayName("商品コードが違えば equals は false")
    void equalsDifferentCode() {
        assertFalse(new Product("A001", "りんご", 120).equals(new Product("A002", "りんご", 120)),
                "コードが違えば、名前と価格が同じでも別の商品(false)です");
    }

    @Test
    @DisplayName("null や Product 以外と比べると false(例外にしない)")
    void equalsNullAndOtherType() {
        Product a = new Product("A001", "りんご", 120);
        assertFalse(a.equals(null),
                "equals(null) は false を返してください(NullPointerException にしない)");
        assertFalse(a.equals("A001"),
                "Product 以外(ここでは文字列)と比べたら false です。instanceof で型を確認してください");
    }

    @Test
    @DisplayName("値引き価格は 価格 × (100 - 値引き率) / 100(切り捨て)")
    void salePrice() {
        assertEquals(240, new DiscountProduct("B002", "みかん", 300, 20).salePrice(),
                "300円の20%OFF は 240円です");
        assertEquals(66, new DiscountProduct("B003", "ぶどう", 99, 33).salePrice(),
                "99円の33%OFF は 99 × 67 / 100 = 66.33 → 切り捨てて 66円です");
        assertEquals(500, new DiscountProduct("B004", "もも", 500, 0).salePrice(),
                "値引き率 0% なら元の価格のままです");
    }

    @Test
    @DisplayName("値引き商品の toString() は親の書式 + 値引き情報")
    void discountToString() {
        assertEquals("[B002] みかん 300円 → 20%OFF 240円",
                new DiscountProduct("B002", "みかん", 300, 20).toString(),
                "DiscountProduct の toString() は super.toString() の後ろに \" → 20%OFF 240円\" を付けた形です");
    }
}
