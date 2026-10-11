import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SalesReportTest {

    @Test
    @DisplayName("例の5日分:合計64000・平均12800.0・最大は4日目(インデックス4)")
    void sampleWeek() {
        int[] sales = {12000, 8000, 15000, 8000, 21000};
        assertEquals(64000L, SalesReport.total(sales), "total は全要素の合計を返してください");
        assertEquals(12800.0, SalesReport.average(sales), 1e-9, "average は 合計 ÷ 日数 を返してください");
        assertEquals(4, SalesReport.bestDay(sales), "bestDay は最大売上の日のインデックス(0始まり)を返してください");
    }

    @Test
    @DisplayName("平均が割り切れないときは小数まで返す")
    void averageHasFraction() {
        int[] sales = {1000, 2000};
        assertEquals(1500.0, SalesReport.average(sales), 1e-9,
                "平均が 1500.0 になりません。int 同士で割っていませんか? (double) にしてから割りましょう");
        assertEquals(1.0 / 3.0, SalesReport.average(new int[]{0, 0, 1}), 1e-9,
                "{0, 0, 1} の平均は 0.333... です。割る前に double に変換してください");
    }

    @Test
    @DisplayName("合計が int の範囲を超えても正しく計算できる")
    void totalDoesNotOverflow() {
        int[] sales = {1_000_000_000, 1_000_000_000, 1_000_000_000};
        assertEquals(3_000_000_000L, SalesReport.total(sales),
                "合計がマイナスや変な値になっていませんか? 足し込む変数を long にしてください");
        assertEquals(1_000_000_000.0, SalesReport.average(sales), 1e-6,
                "平均も long の合計から計算してください");
    }

    @Test
    @DisplayName("最大の日が複数あるときは、いちばん早い日を返す")
    void bestDayTieReturnsEarliest() {
        assertEquals(1, SalesReport.bestDay(new int[]{500, 900, 300, 900}),
                "同じ最大値が複数あるときは最初の日を返してください(>= ではなく > で比べる)");
        assertEquals(0, SalesReport.bestDay(new int[]{700, 700, 700}),
                "全日同じ売上なら 0 を返してください");
    }

    @Test
    @DisplayName("1日分だけでも動く・最大が先頭や末尾でも正しい")
    void singleAndEdges() {
        int[] one = {4200};
        assertEquals(4200L, SalesReport.total(one), "1日分なら合計はその日の売上です");
        assertEquals(4200.0, SalesReport.average(one), 1e-9, "1日分なら平均はその日の売上です");
        assertEquals(0, SalesReport.bestDay(one), "1日分なら bestDay は 0 です");
        assertEquals(0, SalesReport.bestDay(new int[]{9000, 1, 2}), "先頭が最大なら 0 を返してください");
        assertEquals(2, SalesReport.bestDay(new int[]{1, 2, 9000}),
                "末尾が最大なら 2 を返してください。ループの条件が i < sales.length になっているか確認しましょう");
    }

    @Test
    @DisplayName("null や空配列のときは 0 / 0.0 / -1 を返す")
    void nullAndEmpty() {
        assertEquals(0L, SalesReport.total(new int[0]), "空配列の合計は 0 です");
        assertEquals(0.0, SalesReport.average(new int[0]), 1e-9,
                "空配列の平均は 0.0 です(0で割らないよう先にチェックしてください)");
        assertEquals(-1, SalesReport.bestDay(new int[0]), "空配列の bestDay は -1 です");
        assertEquals(0L, SalesReport.total(null), "null のときの合計は 0 です。最初に null チェックをしてください");
        assertEquals(0.0, SalesReport.average(null), 1e-9, "null のときの平均は 0.0 です");
        assertEquals(-1, SalesReport.bestDay(null), "null のときの bestDay は -1 です");
    }
}
