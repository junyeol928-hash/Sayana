import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogLevelCounterTest {

    @Test
    @DisplayName("レベルごとの件数を数える")
    void basic() {
        Map<String, Integer> r = LogLevelCounter.count(List.of(
                "10:00:01 INFO 起動しました",
                "10:00:05 ERROR DB接続に失敗",
                "10:00:06 ERROR DB接続に失敗",
                "10:00:07 INFO 再接続を試みます",
                "10:00:09 WARN リトライします",
                "10:00:10 ERROR タイムアウト"));
        assertEquals(3, r.get("ERROR"), "ERROR は3件です。getOrDefault(level, 0) + 1 で数えていますか? 実際の結果: " + r);
        assertEquals(2, r.get("INFO"), "INFO は2件です。実際の結果: " + r);
        assertEquals(1, r.get("WARN"), "WARN は1件です。実際の結果: " + r);
        assertEquals(3, r.size(), "出てきたレベルは ERROR / INFO / WARN の3種類です。実際の結果: " + r);
    }

    @Test
    @DisplayName("一度も出てこないレベルは Map に入れない")
    void absentLevel() {
        Map<String, Integer> r = LogLevelCounter.count(List.of("10:00:01 INFO 起動しました"));
        assertFalse(r.containsKey("ERROR"),
                "出てこなかったレベル(ERROR)のキーが入っています。件数0のキーは作らないでください");
        assertEquals(Map.of("INFO", 1), r, "INFO が1件だけの Map になるはずです");
    }

    @Test
    @DisplayName("null の行と空白だけの行は読み飛ばす")
    void skipBlankAndNull() {
        List<String> lines = new ArrayList<>(Arrays.asList(
                "10:00:01 INFO 起動しました", null, "", "   ", "10:00:02 INFO 処理開始"));
        Map<String, Integer> r = assertDoesNotThrow(() -> LogLevelCounter.count(lines),
                "null や空白の行で例外が出ています。line == null || line.isBlank() なら continue してください");
        assertEquals(Map.of("INFO", 2), r, "null と空白の行を除くと INFO が2件です。実際の結果: " + r);
    }

    @Test
    @DisplayName("空のリストなら空の Map、null なら空の Map")
    void emptyAndNull() {
        assertTrue(LogLevelCounter.count(List.of()).isEmpty(), "空のリストなら空の Map を返してください");
        Map<String, Integer> r = assertDoesNotThrow(() -> LogLevelCounter.count(null),
                "lines が null のときに例外が出ています。最初に null チェックをしてください");
        assertNotNull(r, "lines が null でも、null ではなく空の Map を返してください");
        assertTrue(r.isEmpty(), "lines が null なら空の Map を返してください");
    }
}
