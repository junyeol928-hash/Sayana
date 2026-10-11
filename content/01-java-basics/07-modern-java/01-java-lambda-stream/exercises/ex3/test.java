import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SalesRankingTest {

    private static List<Product> sample() {
        return List.of(
                new Product("マグカップ", 12),
                new Product("トートバッグ", 30),
                new Product("ボールペン", 12),
                new Product("ノート", 0),
                new Product("付箋", 7));
    }

    @Test
    @DisplayName("TOP1 は一番売れたトートバッグ")
    void top1() {
        assertEquals("トートバッグ", SalesRanking.topSellers(sample(), 1),
                "販売数の多い順に並べてください。少ない順になっていませんか?(reversed() を忘れていないか確認)");
    }

    @Test
    @DisplayName("販売数が同じなら商品名の辞書順(ボールペン → マグカップ)")
    void tieBreak() {
        assertEquals("トートバッグ, ボールペン, マグカップ", SalesRanking.topSellers(sample(), 3),
                "12個で同数のボールペンとマグカップは、名前の辞書順(ボ → マ)に並べます。区切りは \", \"(カンマ+半角スペース)です");
    }

    @Test
    @DisplayName("n が商品数より多ければ全部。販売数0のノートは載せない")
    void moreThanSize() {
        assertEquals("トートバッグ, ボールペン, マグカップ, 付箋", SalesRanking.topSellers(sample(), 10),
                "販売数 0 のノートは除外し、残り4件をすべて並べてください");
    }

    @Test
    @DisplayName("n が 0 なら空文字")
    void zero() {
        assertEquals("", SalesRanking.topSellers(sample(), 0),
                "n が 0 のときは空文字 \"\" を返してください(null ではありません)");
    }

    @Test
    @DisplayName("n が負の数でも例外にせず空文字(境界値)")
    void negative() {
        assertEquals("", SalesRanking.topSellers(sample(), -1),
                "n が負のときも \"\" を返してください。limit(-1) は IllegalArgumentException になるので、先にガード節ではじきます");
    }

    @Test
    @DisplayName("売れた商品が1つもなければ空文字")
    void noneSold() {
        List<Product> none = List.of(new Product("ノート", 0), new Product("付箋", 0));
        assertEquals("", SalesRanking.topSellers(none, 3),
                "販売数がすべて 0 なら、ランキングは空(\"\")です");
        assertEquals("", SalesRanking.topSellers(List.of(), 3),
                "商品リストが空なら \"\" を返してください");
    }
}
