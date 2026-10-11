import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @Test
    @DisplayName("AppConfig で起動すると、メールで通知される")
    void prodUsesMail() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            List<String> sent = ctx.getBean(ReminderService.class)
                    .remind(List.of("tanaka@example.com"), "勤怠を締めてください");
            assertEquals(List.of("[MAIL from=noreply@example.com to=tanaka@example.com] 勤怠を締めてください"), sent,
                    "AppConfig の @Bean notifier() は new MailNotifier(\"noreply@example.com\") を返してください");
        }
    }

    @Test
    @DisplayName("DevConfig に替えるだけで、コンソール通知に切り替わる")
    void devUsesConsole() {
        try (var ctx = new AnnotationConfigApplicationContext(DevConfig.class)) {
            assertInstanceOf(ConsoleNotifier.class, ctx.getBean(Notifier.class),
                    "DevConfig の @Bean notifier() は ConsoleNotifier を返してください");
            List<String> sent = ctx.getBean(ReminderService.class)
                    .remind(List.of("sato@example.com"), "打刻漏れがあります");
            assertEquals(List.of("[CONSOLE to=sato@example.com] 打刻漏れがあります"), sent,
                    "DevConfig の reminderService は、引数で受け取った Notifier を使って作ってください");
        }
    }

    @Test
    @DisplayName("複数の宛先に、順番どおりに送る")
    void remindsAllInOrder() {
        try (var ctx = new AnnotationConfigApplicationContext(DevConfig.class)) {
            List<String> sent = ctx.getBean(ReminderService.class)
                    .remind(List.of("a@example.com", "b@example.com", "c@example.com"), "締め日です");
            assertEquals(List.of(
                    "[CONSOLE to=a@example.com] 締め日です",
                    "[CONSOLE to=b@example.com] 締め日です",
                    "[CONSOLE to=c@example.com] 締め日です"), sent,
                    "members の全員に、渡された順番どおりに send してください");
        }
    }

    @Test
    @DisplayName("宛先が空なら、空の List を返す(境界値)")
    void emptyMembers() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            List<String> sent = ctx.getBean(ReminderService.class).remind(List.of(), "締め日です");
            assertTrue(sent != null && sent.isEmpty(), "宛先が0人なら空の List を返してください(null は返さない)");
        }
    }

    @Test
    @DisplayName("@Configuration のおかげで、notifier() を呼んでも Bean は1つだけ(同じインスタンス)")
    void configurationKeepsSingleton() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            assertEquals(1, ctx.getBeansOfType(Notifier.class).size(), "Notifier の Bean は1つだけのはずです");
            assertSame(ctx.getBean(Notifier.class), ctx.getBean(ReminderService.class).notifier(),
                    "ReminderService が持つ Notifier は、コンテナの Notifier Bean と同じインスタンスのはずです。"
                            + "AppConfig に @Configuration が付いているか確認してください(付けないと notifier() が別の new になります)");
        }
    }
}
