import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderCsvTest {

    private static final String CSV = """
            code,name,price,qty
            A001,コーヒー豆,1200,3
            B002, マグカップ , 800 , 2

            C003,ドリッパー,1500,1
            """;

    @Test
    @DisplayName("1明細の金額は 単価 × 数量(列の前後に空白があってもOK)")
    void lineTotal() {
        assertEquals(3600, OrderCsv.lineTotal("A001,コーヒー豆,1200,3"),
                "\"A001,コーヒー豆,1200,3\" は 1200 × 3 = 3600 です。単価はインデックス2、数量はインデックス3の列です");
        assertEquals(1600, OrderCsv.lineTotal("B002, マグカップ , 800 , 2"),
                "\" 800 \" のように空白が付いた列があります。parseInt の前に strip() してください");
        assertEquals(0, OrderCsv.lineTotal("D004,おまけ,0,5"), "単価 0 円なら金額は 0 です");
    }

    @Test
    @DisplayName("sumAll は見出し行と空行を飛ばして合計する")
    void sumAll() {
        assertEquals(6700, OrderCsv.sumAll(CSV),
                "合計は 3600 + 1600 + 1500 = 6700 です。1行目の見出しを集計していないか、空行を飛ばしているか確認してください");
    }

    @Test
    @DisplayName("見出し行だけ・null なら合計 0(境界値)")
    void sumAllEmpty() {
        assertEquals(0, OrderCsv.sumAll("code,name,price,qty\n"), "見出し行しかなければ合計は 0 です");
        assertEquals(0, OrderCsv.sumAll(null), "csv が null なら 0 を返してください");
    }

    @Test
    @DisplayName("topItem は金額が最大の商品名を、前後の空白を除いて返す")
    void topItem() {
        assertEquals("コーヒー豆", OrderCsv.topItem(CSV), "金額最大は 3600 円のコーヒー豆です");
        String csv = "code,name,price,qty\nB002, マグカップ , 800 , 9\nA001,コーヒー豆,1200,3\n";
        assertEquals("マグカップ", OrderCsv.topItem(csv),
                "金額最大は 7200 円のマグカップです。商品名の前後の空白も strip() で除いてください");
    }

    @Test
    @DisplayName("topItem は同額なら先の明細、明細なし・null なら空文字")
    void topItemEdge() {
        String tie = "code,name,price,qty\nX1,ペン,100,3\nX2,ノート,300,1\n";
        assertEquals("ペン", OrderCsv.topItem(tie), "同じ金額なら先に出てきた明細(ペン)を返してください。比較は >= ではなく > にします");
        assertEquals("", OrderCsv.topItem("code,name,price,qty\n"), "明細がなければ空文字 \"\" を返してください");
        assertEquals("", OrderCsv.topItem(null), "csv が null なら空文字 \"\" を返してください");
    }
}
