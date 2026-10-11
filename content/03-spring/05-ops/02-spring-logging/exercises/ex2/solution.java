import java.util.Map;

public class LogLevelResolver {

    /** loggerName に効くレベルを返す(一番長い一致 → root → INFO) */
    public static Level resolve(Map<String, Level> config, String loggerName) {
        String bestKey = null;
        for (String key : config.keySet()) {
            if (key.equals("root")) {
                continue;
            }
            boolean matches = loggerName.equals(key) || loggerName.startsWith(key + ".");
            if (matches && (bestKey == null || key.length() > bestKey.length())) {
                bestKey = key;
            }
        }
        if (bestKey != null) {
            return config.get(bestKey);
        }
        return config.getOrDefault("root", Level.INFO);
    }

    /** loggerName のロガーで level のログが出力されるなら true */
    public static boolean isEnabled(Map<String, Level> config, String loggerName, Level level) {
        return level.compareTo(resolve(config, loggerName)) >= 0;
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
