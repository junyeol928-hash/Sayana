import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeFormatterTest {

    @Test
    @DisplayName("59秒 → 0:00:59")
    void secondsOnly() {
        assertEquals("0:00:59", TimeFormatter.format(59),
                "59秒は 0:00:59 になるはずです。秒は totalSeconds を 60 で割った余り(%)で求めます。");
    }

    @Test
    @DisplayName("60秒 → 0:01:00(ちょうど1分)")
    void oneMinute() {
        assertEquals("0:01:00", TimeFormatter.format(60),
                "60秒はちょうど1分なので 0:01:00 になるはずです。分は「時間を除いた残りの秒数 / 60」で求めます。");
    }

    @Test
    @DisplayName("3599秒 → 0:59:59(1時間の直前)")
    void justBeforeOneHour() {
        assertEquals("0:59:59", TimeFormatter.format(3599),
                "3599秒は 0:59:59 になるはずです。分が 59 を超えていませんか? 時間の分を差し引いた残り(totalSeconds % 3600)から分を計算しましょう。");
    }

    @Test
    @DisplayName("3600秒 → 1:00:00(ちょうど1時間)")
    void oneHour() {
        assertEquals("1:00:00", TimeFormatter.format(3600),
                "3600秒はちょうど1時間なので 1:00:00 になるはずです。時間は totalSeconds / 3600 で求めます。");
    }

    @Test
    @DisplayName("30615秒 → 8:30:15(定時勤務+残業)")
    void typicalWorkday() {
        assertEquals("8:30:15", TimeFormatter.format(30615),
                "30615秒は 8時間30分15秒 なので 8:30:15 になるはずです。");
    }

    @Test
    @DisplayName("90000秒 → 25:00:00(24時間を超えてもそのまま)")
    void overOneDay() {
        assertEquals("25:00:00", TimeFormatter.format(90000),
                "90000秒は 25:00:00 になるはずです。勤務時間なので 24 で割った余りにはせず、時間はそのまま表示します。");
    }
}
