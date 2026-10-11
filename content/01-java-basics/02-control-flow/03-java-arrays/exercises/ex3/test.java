import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkLogTest {

    @Test
    @DisplayName("reversed:古い順 {30, 0, 45, 60} を新しい順 {60, 45, 0, 30} にする")
    void reversedBasic() {
        int[] result = WorkLog.reversed(new int[]{30, 0, 45, 60});
        assertArrayEquals(new int[]{60, 45, 0, 30}, result,
                "逆順になっていません。実際の結果: " + Arrays.toString(result));
        int[] odd = WorkLog.reversed(new int[]{1, 2, 3, 4, 5});
        assertArrayEquals(new int[]{5, 4, 3, 2, 1}, odd,
                "要素数が奇数のときも逆順にしてください。実際の結果: " + Arrays.toString(odd));
    }

    @Test
    @DisplayName("reversed:元の配列を壊さず、新しい配列を返す")
    void reversedKeepsOriginal() {
        int[] history = {10, 20, 30};
        int[] result = WorkLog.reversed(history);
        assertArrayEquals(new int[]{10, 20, 30}, history,
                "元の配列が書き換えられています。新しい配列を作って、そちらに詰めてください");
        assertNotSame(history, result,
                "引数の配列そのものを返しています。new int[history.length] で新しい配列を作って返してください");
        assertArrayEquals(new int[]{30, 20, 10}, result, "逆順の結果が正しくありません");
    }

    @Test
    @DisplayName("reversed:要素1個・空配列・null")
    void reversedEdges() {
        int[] single = {42};
        int[] r1 = WorkLog.reversed(single);
        assertArrayEquals(new int[]{42}, r1, "要素1個なら同じ並びの配列を返してください");
        assertNotSame(single, r1, "要素1個のときも新しい配列を返してください");

        int[] empty = new int[0];
        int[] r2 = WorkLog.reversed(empty);
        assertNotNull(r2, "空配列のときは null ではなく長さ0の配列を返してください");
        assertEquals(0, r2.length, "空配列のときは長さ0の配列を返してください");

        int[] r3 = WorkLog.reversed(null);
        assertNotNull(r3, "null のときは長さ0の配列を返してください(null を返さない)");
        assertEquals(0, r3.length, "null のときは長さ0の配列を返してください");
    }

    @Test
    @DisplayName("rowTotals:社員ごとの合計(全員同じ日数)")
    void rowTotalsRectangular() {
        int[][] minutes = {
            {480, 480, 480},
            {450, 500, 470},
        };
        int[] result = WorkLog.rowTotals(minutes);
        assertArrayEquals(new int[]{1440, 1420}, result,
                "行ごとの合計が正しくありません。実際の結果: " + Arrays.toString(result));
    }

    @Test
    @DisplayName("rowTotals:行ごとに長さが違う(ジャグ配列)・空の行があっても動く")
    void rowTotalsJagged() {
        int[][] minutes = {
            {480, 480, 450},
            {240, 240},
            {},
            {300, 300, 300, 300, 300},
        };
        int[] result = WorkLog.rowTotals(minutes);
        assertArrayEquals(new int[]{1410, 480, 0, 1500}, result,
                "ジャグ配列の合計が正しくありません。内側のループの上限を minutes[i].length にしていますか? 実際の結果: "
                        + Arrays.toString(result));
    }

    @Test
    @DisplayName("rowTotals:空配列・null のときは長さ0の配列を返す")
    void rowTotalsNullAndEmpty() {
        int[] r1 = WorkLog.rowTotals(new int[0][]);
        assertNotNull(r1, "社員0人のときは null ではなく長さ0の配列を返してください");
        assertEquals(0, r1.length, "社員0人のときは長さ0の配列を返してください");
        int[] r2 = WorkLog.rowTotals(null);
        assertNotNull(r2, "null のときは長さ0の配列を返してください(null を返さない)");
        assertEquals(0, r2.length, "null のときは長さ0の配列を返してください");
    }
}
