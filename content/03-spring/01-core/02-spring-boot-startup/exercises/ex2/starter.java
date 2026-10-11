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
        // TODO: "GET /orders 200" の形の1行を sink.write に渡す
    }
}

/** 「LogSink 型の Bean がまだ無い」ときだけ true */
class OnMissingLogSinkCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // TODO: context.getBeanFactory().getBeanNamesForType(LogSink.class, true, false) が
        //       空のときだけ true を返す
        return true;
    }
}

/** ライブラリ側の「自動設定」のつもり */
// TODO: @Configuration を付ける
public class LoggingAutoConfig {

    // TODO: @Bean と @Conditional(OnMissingLogSinkCondition.class) を付ける
    LogSink logSink() {
        return new ConsoleLogSink();
    }

    // TODO: @Bean を付ける
    AccessLogger accessLogger(LogSink sink) {
        return new AccessLogger(sink);
    }
}
