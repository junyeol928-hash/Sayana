import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkTimeTest {

    private static Shift shift(int d1, int h1, int m1, int d2, int h2, int m2, int breakMin) {
        return new Shift(LocalDateTime.of(2024, 4, d1, h1, m1),
                LocalDateTime.of(2024, 4, d2, h2, m2), breakMin);
    }

    @Test
    @DisplayName("日勤:9:00〜18:00、休憩60分なら実働8時間")
    void dayShift() {
        assertEquals(Duration.ofHours(8), WorkTime.actual(shift(1, 9, 0, 1, 18, 0, 60)),
                "9:00〜18:00 から休憩60分を引くと8時間です。minusMinutes の戻り値を使っていますか?");
        assertEquals(Duration.ofMinutes(7 * 60 + 30), WorkTime.actual(shift(1, 9, 15, 1, 17, 45, 60)),
                "9:15〜17:45 から休憩60分を引くと7時間30分です");
    }

    @Test
    @DisplayName("夜勤:22:00〜翌6:00、休憩60分なら実働7時間(日付またぎ)")
    void nightShift() {
        assertEquals(Duration.ofHours(7), WorkTime.actual(shift(1, 22, 0, 2, 6, 0, 60)),
                "22:00〜翌6:00 から休憩60分を引くと7時間です");
    }

    @Test
    @DisplayName("終了が開始以前、休憩がマイナス・長すぎるなら IllegalArgumentException")
    void invalid() {
        assertThrows(IllegalArgumentException.class, () -> WorkTime.actual(shift(1, 18, 0, 1, 9, 0, 0)),
                "終了(9:00)が開始(18:00)より前なら IllegalArgumentException にしてください");
        assertThrows(IllegalArgumentException.class, () -> WorkTime.actual(shift(1, 9, 0, 1, 9, 0, 0)),
                "開始と終了が同じなら IllegalArgumentException にしてください(isAfter で判定します)");
        assertThrows(IllegalArgumentException.class, () -> WorkTime.actual(shift(1, 9, 0, 1, 18, 0, -10)),
                "休憩がマイナスなら IllegalArgumentException にしてください");
        assertThrows(IllegalArgumentException.class, () -> WorkTime.actual(shift(1, 9, 0, 1, 10, 0, 90)),
                "1時間の勤務で休憩90分なら実働がマイナスになるので IllegalArgumentException にしてください");
    }

    @Test
    @DisplayName("合計:複数シフトを足し合わせる。空なら0")
    void total() {
        List<Shift> shifts = List.of(
                shift(1, 9, 0, 1, 18, 0, 60),   // 8h
                shift(2, 22, 0, 3, 6, 0, 60),   // 7h
                shift(4, 13, 0, 4, 17, 30, 0)); // 4h30m
        assertEquals(Duration.ofMinutes(19 * 60 + 30), WorkTime.total(shifts),
                "8時間 + 7時間 + 4時間30分 = 19時間30分です。sum = sum.plus(...) と受け取り直していますか?");
        assertEquals(Duration.ZERO, WorkTime.total(List.of()),
                "シフトがなければ Duration.ZERO を返してください");
    }

    @Test
    @DisplayName("表示:「7時間30分」形式。24時間を超えても時間で数える")
    void format() {
        assertEquals("7時間30分", WorkTime.format(Duration.ofMinutes(450)),
                "450分は「7時間30分」と表示してください");
        assertEquals("8時間0分", WorkTime.format(Duration.ofHours(8)),
                "8時間ちょうどは「8時間0分」と表示してください");
        assertEquals("25時間5分", WorkTime.format(Duration.ofMinutes(25 * 60 + 5)),
                "25時間5分は「25時間5分」です。toHoursPart() だと 1 になってしまうので toHours() を使ってください");
        assertEquals("0時間0分", WorkTime.format(Duration.ZERO),
                "0 は「0時間0分」と表示してください");
    }
}
