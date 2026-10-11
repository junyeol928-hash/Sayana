import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogLevelCounter {

    public static Map<String, Integer> count(List<String> lines) {
        Map<String, Integer> result = new HashMap<>();
        // TODO: lines が null なら空の Map(result)をそのまま返す

        // TODO: 拡張 for で1行ずつ処理する
        //   - null や空白だけの行は continue で読み飛ばす
        //   - line.split(" ")[1] でレベルを取り出す
        //   - getOrDefault を使って件数を +1 する

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
