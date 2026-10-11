import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyReportTest {

    @Test
    @DisplayName("売上日報の見出しと本文")
    void dailyParts() {
        DailyReport r = new DailyReport(12000, 30);
        assertEquals("売上日報", r.title(),
                "DailyReport の title() は \"売上日報\" を返してください");
        assertEquals("売上: 12000円 / 客数: 30人", r.body(),
                "DailyReport の body() は \"売上: 12000円 / 客数: 30人\" の形式です。区切りの \" / \" や単位を確認してください");
    }

    @Test
    @DisplayName("売上日報を print() すると親の流れどおりに3行で出る")
    void dailyPrint() {
        String expected = "【売上日報】\n売上: 0円 / 客数: 0人\n-- 以上 --";
        assertEquals(expected, new DailyReport(0, 0).print(),
                "print() の結果が違います。title() と body() が正しい文字列を返しているか確認してください");
    }

    @Test
    @DisplayName("在庫が 10 個ちょうどなら「要発注」は付かない(境界値)")
    void stockTen() {
        assertEquals("コピー用紙: 10個", new StockReport("コピー用紙", 10).body(),
                "在庫 10 個は「10 未満」ではないので「要発注」は付けません。< と <= を確認してください");
    }

    @Test
    @DisplayName("在庫が 9 個なら末尾に「 要発注」が付く(境界値)")
    void stockNine() {
        assertEquals("トナー: 9個 要発注", new StockReport("トナー", 9).body(),
                "在庫が 10 未満なら末尾に \" 要発注\"(先頭に半角スペース)を付けてください");
    }

    @Test
    @DisplayName("在庫報告を Report 型で扱って print() できる")
    void stockAsReport() {
        Report r = new StockReport("封筒", 0);
        assertEquals("【在庫報告】\n封筒: 0個 要発注\n-- 以上 --", r.print(),
                "StockReport の title() は \"在庫報告\"、在庫 0 個なら本文は \"封筒: 0個 要発注\" です");
    }
}
