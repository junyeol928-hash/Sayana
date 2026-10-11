import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** ログの出力先 */
interface LogSink {
    void write(String line);
}

/** 既定:標準出力に書く */
class ConsoleLogSink implements LogSink {
    @Override
    public void write(String line) {
        System.out.println(line);
    }
}

/** 利用者が用意する出力先:メモリに貯める(テストで中身を確かめられる) */
class MemoryLogSink implements LogSink {
    private final List<String> lines = new ArrayList<>();

    @Override
    public void write(String line) {
        lines.add(line);
    }

    List<String> lines() {
        return List.copyOf(lines);
    }
}

/** 利用者(アプリ側)の設定 */
@Configuration
class UserLogConfig {
    @Bean
    LogSink memoryLogSink() {
        return new MemoryLogSink();
    }
}

class AccessLogger {
    private final LogSink sink;

    AccessLogger(LogSink sink) {
        this.sink = sink;
    }

    LogSink sink() {
        return sink;
    }

    void log(String method, String path, int status) {
        sink.write(method + " " + path + " " + status);
    }
}

/** 「LogSink 型の Bean がまだ無い」ときだけ true */
class OnMissingLogSinkCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String[] names = context.getBeanFactory()
                .getBeanNamesForType(LogSink.class, true, false);
        return names.length == 0;
    }
}

/** ライブラリ側の「自動設定」のつもり */
@Configuration
public class LoggingAutoConfig {

    @Bean
    @Conditional(OnMissingLogSinkCondition.class)
    LogSink logSink() {
        return new ConsoleLogSink();
    }

    @Bean
    AccessLogger accessLogger(LogSink sink) {
        return new AccessLogger(sink);
    }
}
