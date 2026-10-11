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
        // TODO: members の各宛先に notifier.send(宛先, message) を呼び、戻り値を results に追加する

        return results;
    }
}

/** 本番用の設定 */
// TODO: @Configuration を付ける
public class AppConfig {

    // TODO: @Bean を付け、MailNotifier("noreply@example.com") を返す
    Notifier notifier() {
        return null;
    }

    // TODO: @Bean を付け、notifier() を使って ReminderService を作って返す
    ReminderService reminderService() {
        return null;
    }
}

/** 開発用の設定 */
// TODO: @Configuration を付ける
class DevConfig {

    // TODO: @Bean を付け、ConsoleNotifier を返す
    Notifier notifier() {
        return null;
    }

    // TODO: @Bean を付け、引数で受け取った notifier で ReminderService を作って返す
    ReminderService reminderService(Notifier notifier) {
        return null;
    }
}
