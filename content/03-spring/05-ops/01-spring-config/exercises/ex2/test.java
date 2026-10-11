import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NotifierConfigTest {

    /** プロファイルと設定値を準備してから NotifierConfig を登録し、Notifier 型の Bean をすべて取り出す */
    private static Map<String, Notifier> load(Map<String, Object> settings, String... profiles) {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().setActiveProfiles(profiles);
            ctx.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("test-settings", settings));
            ctx.register(NotifierConfig.class);
            ctx.refresh();
            return ctx.getBeansOfType(Notifier.class);
        }
    }

    private static Notifier only(Map<String, Notifier> beans, String situation) {
        assertEquals(1, beans.size(), situation + "では Notifier 型の Bean はちょうど1つのはずですが、"
                + beans.size() + " 個登録されています(登録された Bean: " + beans.keySet()
                + ")。@Profile の付け方を確認してください");
        return beans.values().iterator().next();
    }

    @Test
    @DisplayName("プロファイル指定なしなら ConsoleNotifier(本物は送らない)")
    void noProfile() {
        Notifier n = only(load(Map.of()), "プロファイル指定なし");
        assertInstanceOf(ConsoleNotifier.class, n,
                "何も指定しないときは安全側の ConsoleNotifier を使います。consoleNotifier() に @Profile(\"!prod\") を付けましたか?");
    }

    @Test
    @DisplayName("dev プロファイルなら ConsoleNotifier")
    void dev() {
        Notifier n = only(load(Map.of(), "dev"), "dev プロファイル");
        assertInstanceOf(ConsoleNotifier.class, n, "dev のときは ConsoleNotifier を使います");
    }

    @Test
    @DisplayName("prod プロファイルなら SmtpNotifier、host は mail.host の値")
    void prodWithHost() {
        Notifier n = only(load(Map.of("mail.host", "smtp.example.com"), "prod"), "prod プロファイル");
        SmtpNotifier smtp = assertInstanceOf(SmtpNotifier.class, n,
                "prod のときは SmtpNotifier を使います。smtpNotifier() に @Profile(\"prod\") を付けましたか?");
        assertEquals("smtp.example.com", smtp.host(),
                "SMTP サーバ名は設定 mail.host から受け取ります。@Value(\"${mail.host:localhost}\") を確認してください");
    }

    @Test
    @DisplayName("prod で mail.host が無ければ host は既定値 localhost")
    void prodDefaultHost() {
        Notifier n = only(load(Map.of(), "prod"), "prod プロファイル");
        SmtpNotifier smtp = assertInstanceOf(SmtpNotifier.class, n, "prod のときは SmtpNotifier を使います");
        assertEquals("localhost", smtp.host(), "mail.host が無いときは既定値 localhost です");
    }

    @Test
    @DisplayName("dev と prod を同時に有効にしたら prod 扱い(SmtpNotifier だけ)")
    void devAndProd() {
        Notifier n = only(load(Map.of(), "dev", "prod"), "dev と prod の両方が有効なとき");
        assertInstanceOf(SmtpNotifier.class, n,
                "prod が含まれていれば本番扱いです。@Profile(\"dev\") ではなく @Profile(\"!prod\") で「prod 以外」を表してください");
    }
}
