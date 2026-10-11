import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 通知手段 */
interface Notifier {
    String send(String to, String message);
}

/** メールで通知する(ライブラリのクラスのつもり) */
class MailNotifier implements Notifier {
    private final String from;

    MailNotifier(String from) {
        this.from = from;
    }

    @Override
    public String send(String to, String message) {
        return "[MAIL from=" + from + " to=" + to + "] " + message;
    }
}

/** 開発用:コンソールに出すだけ */
class ConsoleNotifier implements Notifier {
    @Override
    public String send(String to, String message) {
        return "[CONSOLE to=" + to + "] " + message;
    }
}

class ReminderService {
    private final Notifier notifier;

    ReminderService(Notifier notifier) {
        this.notifier = notifier;
    }

    Notifier notifier() {
        return notifier;
    }

    List<String> remind(List<String> members, String message) {
        List<String> results = new ArrayList<>();
        for (String to : members) {
            results.add(notifier.send(to, message));
        }
        return results;
    }
}

/** 本番用の設定 */
@Configuration
public class AppConfig {

    @Bean
    Notifier notifier() {
        return new MailNotifier("noreply@example.com");
    }

    @Bean
    ReminderService reminderService() {
        // プロキシ経由なので、notifier() はコンテナの同じ Bean を返す
        return new ReminderService(notifier());
    }
}

/** 開発用の設定 */
@Configuration
class DevConfig {

    @Bean
    Notifier notifier() {
        return new ConsoleNotifier();
    }

    @Bean
    ReminderService reminderService(Notifier notifier) {
        return new ReminderService(notifier);
    }
}
