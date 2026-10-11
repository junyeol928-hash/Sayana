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
        // TODO: values.get(key) を ofNullable で包み、
        //       strip で前後の空白を除いて、空文字なら filter で落とす
        return Optional.empty();
    }

    public int getInt(String key, int defaultValue) {
        // TODO: get(key) の結果を flatMap(AppConfig::tryParseInt) で数値にし、
        //       取れなければ defaultValue を返す
        return 0;
    }

    public String require(String key) {
        // TODO: get(key) が空なら IllegalStateException を投げる
        //       メッセージは "必須の設定がありません: " + key
        return "";
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
