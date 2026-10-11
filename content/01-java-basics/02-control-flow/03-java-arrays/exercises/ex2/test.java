import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScoreStatsTest {

    @Test
    @DisplayName("例の5人:合格3人・平均(67.2点)以上2人")
    void sample() {
        int[] scores = {80, 45, 60, 92, 59};
        assertEquals(3, ScoreStats.countPassed(scores), "60点以上の人数を数えてください");
        assertEquals(2, ScoreStats.countAtOrAboveAverage(scores), "平均 67.2 点以上の人数を数えてください");
    }

    @Test
    @DisplayName("合格ラインちょうど(60点)は合格、59点は不合格")
    void passBoundary() {
        assertEquals(1, ScoreStats.countPassed(new int[]{60}), "60点ちょうどは合格です。> ではなく >= で比べてください");
        assertEquals(0, ScoreStats.countPassed(new int[]{59}), "59点は不合格です");
        assertEquals(3, ScoreStats.countPassed(new int[]{100, 0, 60, 61, 59}), "{100, 0, 60, 61, 59} の合格者は3人です");
    }

    @Test
    @DisplayName("平均が小数のとき、切り捨てずに比べる")
    void averageWithFraction() {
        assertEquals(1, ScoreStats.countAtOrAboveAverage(new int[]{1, 2}),
                "{1, 2} の平均は 1.5 なので平均以上は1人です。int 同士で割って 1 になっていませんか?");
        assertEquals(1, ScoreStats.countAtOrAboveAverage(new int[]{70, 70, 71}),
                "{70, 70, 71} の平均は 70.33... なので平均以上は1人です");
    }

    @Test
    @DisplayName("平均点ちょうどの人も「平均以上」に含める")
    void averageIncludesEqual() {
        assertEquals(3, ScoreStats.countAtOrAboveAverage(new int[]{50, 50, 50}),
                "全員同じ点なら全員が平均ちょうどなので3人です。>= で比べてください");
        assertEquals(2, ScoreStats.countAtOrAboveAverage(new int[]{40, 60, 80}),
                "{40, 60, 80} の平均は 60 なので、60 と 80 の2人です");
    }

    @Test
    @DisplayName("null や空配列のときはどちらも 0 を返す")
    void nullAndEmpty() {
        assertEquals(0, ScoreStats.countPassed(new int[0]), "空配列の合格者数は 0 です");
        assertEquals(0, ScoreStats.countAtOrAboveAverage(new int[0]),
                "空配列なら 0 を返してください(0人で割らないよう先にチェック)");
        assertEquals(0, ScoreStats.countPassed(null), "null のときは 0 を返してください。最初に null チェックをしましょう");
        assertEquals(0, ScoreStats.countAtOrAboveAverage(null), "null のときは 0 を返してください");
    }
}
