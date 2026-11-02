import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductSorterTest {

    @Test
    @DisplayName("byPriceDesc() は匿名クラスの Comparator を返す")
    void isAnonymousClass() {
        Comparator<Product> c = ProductSorter.byPriceDesc();
        assertNotNull(c, "byPriceDesc() が null を返しています");
        assertTrue(c.getClass().isAnonymousClass(),
                "new Comparator<Product>() { ... } の匿名クラスで書いてください(ラムダ式や名前付きクラスではなく)");
    }

    @Test
    @DisplayName("価格の高い方が前になる")
    void higherPriceFirst() {
        Comparator<Product> c = ProductSorter.byPriceDesc();
        Product cheap = new Product("ノート", 120);
        Product expensive = new Product("ファイル", 300);
        assertTrue(c.compare(expensive, cheap) < 0,
                "高い商品(300円)を前にするには compare(高い, 安い) が負になる必要があります。Integer.compare の引数の順番を確認してください");
        assertTrue(c.compare(cheap, expensive) > 0,
                "compare(安い, 高い) は正になる必要があります(安い方が後ろ)");
    }

    @Test
    @DisplayName("価格が同じなら商品名の辞書順")
    void samePriceByName() {
        Comparator<Product> c = ProductSorter.byPriceDesc();
        Product a = new Product("ノート", 120);
        Product b = new Product("付箋", 120);
        assertTrue(c.compare(a, b) < 0,
                "同じ価格のときは a.name.compareTo(b.name) の結果を返してください(\"ノート\" が \"付箋\" より前)");
        assertTrue(c.compare(b, a) > 0,
                "同じ価格のときは名前の辞書順です。compare(付箋, ノート) は正になります");
        assertEquals(0, c.compare(a, new Product("ノート", 120)),
                "価格も名前も同じなら 0 を返してください");
    }

    @Test
    @DisplayName("sortForShelf で配列が並べ替わる")
    void sortForShelf() {
        Product[] items = {
            new Product("ノート", 120),
            new Product("ペン", 150),
            new Product("付箋", 120),
            new Product("ファイル", 300)
        };
        ProductSorter.sortForShelf(items);
        assertEquals("[ファイル(300), ペン(150), ノート(120), 付箋(120)]", Arrays.toString(items),
                "sortForShelf の中で Arrays.sort(items, byPriceDesc()) を呼んで、受け取った配列を並べ替えてください");
    }

    @Test
    @DisplayName("大きな金額でも正しく比べられる")
    void largePrices() {
        Comparator<Product> c = ProductSorter.byPriceDesc();
        Product big = new Product("サーバー", 2_000_000_000);
        Product minus = new Product("返金", -2_000_000_000);
        assertTrue(c.compare(big, minus) < 0,
                "大きな金額で順番が逆になっています。引き算(b.price - a.price)ではなく Integer.compare を使ってください");
    }
}
