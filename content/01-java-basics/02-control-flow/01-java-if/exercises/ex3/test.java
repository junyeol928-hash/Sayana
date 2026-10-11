import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HourlyRateTest {

    @Test
    @DisplayName("平日(月曜・金曜)の日勤は 1200 円")
    void weekday() {
        assertEquals(1200, HourlyRate.rate(1, false, false), "月曜(1)の日勤は 1200 円です");
        assertEquals(1200, HourlyRate.rate(5, false, false), "金曜(5)も平日なので 1200 円です。case に 5 が入っているか確認してください");
    }

    @Test
    @DisplayName("土曜は 1300 円、日曜は 1500 円")
    void weekend() {
        assertEquals(1300, HourlyRate.rate(6, false, false), "土曜(6)は 1300 円です");
        assertEquals(1500, HourlyRate.rate(7, false, false), "日曜(7)は 1500 円です");
    }

    @Test
    @DisplayName("祝日は曜日に関係なく 1500 円(平日でも土曜でも)")
    void publicHoliday() {
        assertEquals(1500, HourlyRate.rate(3, true, false), "水曜でも祝日なら 1500 円です。祝日の判定を曜日の判定より優先していますか?");
        assertEquals(1500, HourlyRate.rate(6, true, false), "土曜でも祝日なら 1500 円です(土曜料金 1300 円ではありません)");
    }

    @Test
    @DisplayName("夜勤は基本時給に 250 円加算")
    void night() {
        assertEquals(1450, HourlyRate.rate(1, false, true), "月曜の夜勤は 1200 + 250 = 1450 円です");
        assertEquals(1750, HourlyRate.rate(7, false, true), "日曜の夜勤は 1500 + 250 = 1750 円です");
        assertEquals(1750, HourlyRate.rate(2, true, true), "祝日の夜勤は 1500 + 250 = 1750 円です");
    }

    @Test
    @DisplayName("曜日番号が 0 や 8 なら -1(境界値)")
    void invalidDay() {
        assertEquals(-1, HourlyRate.rate(0, false, false), "曜日番号 0 は不正なので -1 を返してください");
        assertEquals(-1, HourlyRate.rate(8, false, false), "曜日番号 8 は不正なので -1 を返してください");
    }

    @Test
    @DisplayName("曜日番号が不正なら、祝日・夜勤でも -1")
    void invalidDayWithFlags() {
        assertEquals(-1, HourlyRate.rate(8, true, true),
                "曜日番号が不正なら祝日・夜勤フラグに関係なく -1 です。範囲チェック(ガード節)を一番最初に書いていますか?");
    }
}
