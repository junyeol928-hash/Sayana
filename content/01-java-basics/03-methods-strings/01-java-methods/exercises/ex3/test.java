import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OvertimeCalcTest {

    // 同じ労働時間が days 日続いた勤怠データを作る
    private static int[] days(int minutes, int days) {
        int[] a = new int[days];
        for (int i = 0; i < days; i++) {
            a[i] = minutes;
        }
        return a;
    }

    @Test
    @DisplayName("1日の残業は 480 分を超えた分(480 ちょうどは 0、481 は 1)")
    void overtimeOfDay() {
        assertEquals(60, OvertimeCalc.overtimeOfDay(540), "540 分働いたら残業は 60 分です");
        assertEquals(0, OvertimeCalc.overtimeOfDay(480), "ちょうど 480 分なら残業は 0 分です");
        assertEquals(1, OvertimeCalc.overtimeOfDay(481), "481 分なら残業は 1 分です");
        assertEquals(0, OvertimeCalc.overtimeOfDay(300), "480 分未満なら残業は 0 分です(マイナスにしない)");
        assertEquals(0, OvertimeCalc.overtimeOfDay(-10), "おかしな値(マイナス)でも残業は 0 分です");
    }

    @Test
    @DisplayName("totalOvertime は何日分でも合計でき、引数なしなら 0")
    void totalOvertime() {
        assertEquals(180, OvertimeCalc.totalOvertime(540, 480, 600),
                "540, 480, 600 の残業合計は 60 + 0 + 120 = 180 分です。各日に overtimeOfDay を使っていますか?");
        assertEquals(0, OvertimeCalc.totalOvertime(), "引数なしで呼んだら 0 です(可変長引数は長さ 0 の配列になります)");
        assertEquals(30, OvertimeCalc.totalOvertime(510), "1日分だけでも計算できるようにしてください");
    }

    @Test
    @DisplayName("totalOvertime / exceedsLimit に null の配列を渡しても例外にならない")
    void nullArray() {
        assertEquals(0, OvertimeCalc.totalOvertime((int[]) null),
                "null の配列が渡されたら 0 を返してください。拡張 for の前に null チェックが必要です");
        assertFalse(OvertimeCalc.exceedsLimit((int[]) null), "null の配列なら残業 0 なので false です");
    }

    @Test
    @DisplayName("toHourMinute は 時間:分 で、分は2桁ゼロ埋め")
    void toHourMinute() {
        assertEquals("3:05", OvertimeCalc.toHourMinute(185), "185 分は 3:05 です。分が 10 未満なら 0 を付けてください");
        assertEquals("0:00", OvertimeCalc.toHourMinute(0), "0 分は 0:00 です");
        assertEquals("10:30", OvertimeCalc.toHourMinute(630), "630 分は 10:30 です。分が 10 以上ならゼロ埋めしません");
        assertEquals("45:00", OvertimeCalc.toHourMinute(2700), "2700 分は 45:00 です");
    }

    @Test
    @DisplayName("残業 2700 分ちょうどは上限内、1分でも超えたら true(境界値)")
    void exceedsLimitBoundary() {
        // 1日 615 分(残業 135 分)× 20 日 = 2700 分
        assertFalse(OvertimeCalc.exceedsLimit(days(615, 20)),
                "残業合計がちょうど 2700 分なら上限「以内」なので false です。>= ではなく > で比べてください");
        // 19日は 615 分、1日だけ 616 分 → 2701 分
        int[] month = days(615, 20);
        month[0] = 616;
        assertTrue(OvertimeCalc.exceedsLimit(month), "残業合計 2701 分は上限超えなので true です");
        assertFalse(OvertimeCalc.exceedsLimit(540, 480, 600), "残業 180 分なら上限内なので false です");
    }
}
