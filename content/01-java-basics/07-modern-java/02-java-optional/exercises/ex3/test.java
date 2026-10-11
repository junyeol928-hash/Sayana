import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {

    private static AppConfig sample() {
        return new AppConfig(Map.of(
                "app.name", " Java道場 ",
                "db.port", "5432",
                "db.timeout", "abc",
                "db.user", "  ",
                "db.retry", " 0 "));
    }

    @Test
    @DisplayName("get:前後の空白を除いた値を返す。キーがなければ空")
    void get() {
        assertEquals(Optional.of("Java道場"), sample().get("app.name"),
                "値の前後の空白は strip で取り除いて Optional に入れてください");
        assertEquals(Optional.empty(), sample().get("app.version"),
                "存在しないキーは空の Optional です。Optional.ofNullable で包んでいますか?");
    }

    @Test
    @DisplayName("get:空白だけの値は未設定扱い(空の Optional)")
    void getBlank() {
        assertEquals(Optional.empty(), sample().get("db.user"),
                "空白だけの値は、strip すると空文字です。filter で落として空の Optional にしてください");
    }

    @Test
    @DisplayName("getInt:数値ならその値(空白つきの \" 0 \" も 0 として読める)")
    void getIntValid() {
        assertEquals(5432, sample().getInt("db.port", 3306), "db.port は 5432 です");
        assertEquals(0, sample().getInt("db.retry", 3),
                "\" 0 \" は空白を除くと 0 という正しい値です。既定値の 3 ではなく 0 を返してください");
    }

    @Test
    @DisplayName("getInt:キーがない・空白だけ・数値でないなら既定値")
    void getIntDefault() {
        assertEquals(10, sample().getInt("db.pool", 10), "キーがないときは既定値 10 を返してください");
        assertEquals(7, sample().getInt("db.user", 7), "空白だけの値も既定値 7 を返してください");
        assertEquals(30, sample().getInt("db.timeout", 30),
                "\"abc\" は数値に変換できないので既定値 30 です。flatMap(AppConfig::tryParseInt) を使っていますか?");
    }

    @Test
    @DisplayName("require:値があればそれを返す")
    void requirePresent() {
        assertEquals("5432", sample().require("db.port"), "db.port は存在するので \"5432\" を返してください");
    }

    @Test
    @DisplayName("require:値が取れなければ、キー名入りメッセージの IllegalStateException")
    void requireMissing() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> sample().require("db.host"),
                "必須の設定がないときは IllegalStateException を投げてください(orElseThrow)");
        assertEquals("必須の設定がありません: db.host", e.getMessage(),
                "例外のメッセージは \"必須の設定がありません: \" + key にしてください");
        assertThrows(IllegalStateException.class, () -> sample().require("db.user"),
                "空白だけの値も「設定なし」として例外にしてください");
    }
}
