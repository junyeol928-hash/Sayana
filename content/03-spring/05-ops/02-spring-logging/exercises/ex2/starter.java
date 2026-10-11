import java.util.Map;

public class LogLevelResolver {

    /** loggerName に効くレベルを返す(一番長い一致 → root → INFO) */
    public static Level resolve(Map<String, Level> config, String loggerName) {
        // TODO: config のキーのうち、loggerName と完全一致するか、loggerName が「キー + "."」で始まるものを探す
        // TODO: 複数あれば一番長いキーの値を返す
        // TODO: 無ければ "root" の値、それも無ければ Level.INFO
        return null; // ← ダミー。書き換えてください
    }

    /** loggerName のロガーで level のログが出力されるなら true */
    public static boolean isEnabled(Map<String, Level> config, String loggerName, Level level) {
        // TODO: level が resolve(...) の結果「以上」なら true(compareTo を使う)
        return false; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        Map<String, Level> config = Map.of(
                "root", Level.WARN,
                "com.sayana.dojo", Level.DEBUG,
                "com.sayana.dojo.ai", Level.INFO);
        System.out.println(resolve(config, "com.sayana.dojo.ai.AiService"));   // INFO
        System.out.println(resolve(config, "com.sayana.dojo.exec.Grader"));    // DEBUG
        System.out.println(resolve(config, "com.sayana.dojox.Foo"));           // WARN
        System.out.println(isEnabled(config, "org.hibernate.SQL", Level.INFO)); // false
    }
}

/** ログレベル(弱い順に宣言しているので、compareTo で大小を比べられる) */
enum Level {
    TRACE, DEBUG, INFO, WARN, ERROR
}
