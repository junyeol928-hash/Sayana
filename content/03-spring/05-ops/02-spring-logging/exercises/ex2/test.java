import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogLevelResolverTest {

    private static final Map<String, Level> CONFIG = Map.of(
            "root", Level.WARN,
            "com.sayana.dojo", Level.DEBUG,
            "com.sayana.dojo.ai", Level.INFO);

    @Test
    @DisplayName("一番長く一致したキーのレベルが効く")
    void longestMatch() {
        assertEquals(Level.INFO, LogLevelResolver.resolve(CONFIG, "com.sayana.dojo.ai.AiService"),
                "com.sayana.dojo と com.sayana.dojo.ai の両方に一致しますが、長い方(com.sayana.dojo.ai)が勝ちます");
        assertEquals(Level.DEBUG, LogLevelResolver.resolve(CONFIG, "com.sayana.dojo.exec.Grader"),
                "com.sayana.dojo.exec.Grader は com.sayana.dojo に一致するので DEBUG です");
    }

    @Test
    @DisplayName("キーとロガー名が完全一致しても効く")
    void exactMatch() {
        assertEquals(Level.DEBUG, LogLevelResolver.resolve(CONFIG, "com.sayana.dojo"),
                "ロガー名がキーと完全に同じときも、そのキーのレベルが効きます");
    }

    @Test
    @DisplayName("ドット区切りで一致しないものは別物(com.sayana.dojox は com.sayana.dojo ではない)")
    void dotBoundary() {
        assertEquals(Level.WARN, LogLevelResolver.resolve(CONFIG, "com.sayana.dojox.Foo"),
                "com.sayana.dojox.Foo は com.sayana.dojo の下ではありません。startsWith(key) ではなく startsWith(key + \".\") で判定してください");
    }

    @Test
    @DisplayName("どれにも一致しなければ root、root も無ければ INFO")
    void fallback() {
        assertEquals(Level.WARN, LogLevelResolver.resolve(CONFIG, "org.hibernate.SQL"),
                "どのキーにも一致しないときは root の値(WARN)です");
        assertEquals(Level.INFO, LogLevelResolver.resolve(Map.of("com.example", Level.ERROR), "org.hibernate.SQL"),
                "root の設定も無いときは Spring Boot の既定の INFO を返してください");
        assertEquals(Level.INFO, LogLevelResolver.resolve(Map.of(), "com.example.App"),
                "設定が空のときも INFO です");
    }

    @Test
    @DisplayName("isEnabled:効いているレベル「以上」なら出力される(境界値)")
    void isEnabled() {
        // com.sayana.dojo.ai は INFO
        assertTrue(LogLevelResolver.isEnabled(CONFIG, "com.sayana.dojo.ai.AiService", Level.INFO),
                "INFO が効いているロガーで INFO のログは出ます(以上なので、ちょうど同じレベルも含む)");
        assertTrue(LogLevelResolver.isEnabled(CONFIG, "com.sayana.dojo.ai.AiService", Level.ERROR),
                "INFO が効いているロガーで ERROR のログは出ます");
        assertFalse(LogLevelResolver.isEnabled(CONFIG, "com.sayana.dojo.ai.AiService", Level.DEBUG),
                "INFO が効いているロガーで DEBUG のログは出ません");
    }

    @Test
    @DisplayName("isEnabled:DEBUG のパッケージでは DEBUG が出て TRACE は出ない、root(WARN)では INFO も出ない")
    void isEnabledOthers() {
        assertTrue(LogLevelResolver.isEnabled(CONFIG, "com.sayana.dojo.exec.Grader", Level.DEBUG),
                "DEBUG が効いているロガーで DEBUG のログは出ます");
        assertFalse(LogLevelResolver.isEnabled(CONFIG, "com.sayana.dojo.exec.Grader", Level.TRACE),
                "DEBUG が効いているロガーで TRACE のログは出ません");
        assertFalse(LogLevelResolver.isEnabled(CONFIG, "org.hibernate.SQL", Level.INFO),
                "root が WARN なので、どれにも一致しないロガーでは INFO は出ません");
    }
}
