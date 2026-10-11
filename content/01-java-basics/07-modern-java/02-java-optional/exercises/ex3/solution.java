import java.util.Map;
import java.util.Optional;

public class AppConfig {

    private final Map<String, String> values;

    public AppConfig(Map<String, String> values) {
        this.values = values;
    }

    /** 文字列を int に変換する。変換できなければ空の Optional(用意済み・変更不要) */
    static Optional<Integer> tryParseInt(String s) {
        try {
            return Optional.of(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key))
                .map(String::strip)
                .filter(s -> !s.isEmpty());
    }

    public int getInt(String key, int defaultValue) {
        return get(key)
                .flatMap(AppConfig::tryParseInt)
                .orElse(defaultValue);
    }

    public String require(String key) {
        return get(key).orElseThrow(
                () -> new IllegalStateException("必須の設定がありません: " + key));
    }

    public static void main(String[] args) {
        AppConfig conf = new AppConfig(Map.of(
                "app.name", " Java道場 ",
                "db.port", "5432",
                "db.timeout", "abc",
                "db.user", "  "));
        System.out.println(conf.get("app.name"));           // Optional[Java道場]
        System.out.println(conf.get("db.user"));            // Optional.empty
        System.out.println(conf.getInt("db.port", 3306));   // 5432
        System.out.println(conf.getInt("db.timeout", 30));  // 30
        System.out.println(conf.getInt("db.pool", 10));     // 10
        try {
            conf.require("db.host");
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());             // 必須の設定がありません: db.host
        }
    }
}
