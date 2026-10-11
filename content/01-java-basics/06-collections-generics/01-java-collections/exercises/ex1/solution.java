import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogLevelCounter {

    public static Map<String, Integer> count(List<String> lines) {
        Map<String, Integer> result = new HashMap<>();
        if (lines == null) {
            return result;
        }
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String level = line.split(" ")[1];
            result.put(level, result.getOrDefault(level, 0) + 1);
        }
        return result;
    }

    public static void main(String[] args) {
        List<String> lines = List.of(
                "10:00:01 INFO 起動しました",
                "10:00:05 ERROR DB接続に失敗",
                "10:00:06 ERROR DB接続に失敗",
                "10:00:09 WARN リトライします");
        System.out.println(count(lines)); // 例: {ERROR=2, INFO=1, WARN=1}(順番は不定)
    }
}
