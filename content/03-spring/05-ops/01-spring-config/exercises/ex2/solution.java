import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class NotifierConfig {

    @Bean
    @Profile("prod")
    Notifier smtpNotifier(@Value("${mail.host:localhost}") String host) {
        return new SmtpNotifier(host);
    }

    @Bean
    @Profile("!prod")
    Notifier consoleNotifier() {
        return new ConsoleNotifier();
    }
}

/** お知らせの送信口(STS では com.example.shop.notify パッケージに置く) */
interface Notifier {
    String send(String to, String message);
}

/** 開発用:本当には送らず、送ったつもりの内容を返すだけ */
class ConsoleNotifier implements Notifier {
    @Override
    public String send(String to, String message) {
        return "[送信しない] " + to + " 宛: " + message;
    }
}

/** 本番用:SMTP サーバ経由で送る(この演習では送信処理は省略) */
class SmtpNotifier implements Notifier {
    private final String host;

    SmtpNotifier(String host) {
        this.host = host;
    }

    String host() {
        return host;
    }

    @Override
    public String send(String to, String message) {
        return "[SMTP " + host + "] " + to + " 宛: " + message;
    }
}
