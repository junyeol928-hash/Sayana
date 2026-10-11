import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LoggingAutoConfigTest {

    @Test
    @DisplayName("利用者の設定なし:既定の ConsoleLogSink が Bean になり、AccessLogger に渡される")
    void defaultSink() {
        try (var ctx = new AnnotationConfigApplicationContext(LoggingAutoConfig.class)) {
            assertInstanceOf(ConsoleLogSink.class, ctx.getBean(LogSink.class),
                    "LogSink の Bean が無いときは logSink() の ConsoleLogSink が登録されるはずです。@Bean を付けたか確認してください");
            assertInstanceOf(ConsoleLogSink.class, ctx.getBean(AccessLogger.class).sink(),
                    "accessLogger(LogSink) に @Bean を付けて、AccessLogger も Bean にしてください");
        }
    }

    @Test
    @DisplayName("利用者が LogSink を定義したら、既定の ConsoleLogSink は登録されない(LogSink は1つだけ)")
    void backsOffWhenUserDefines() {
        try (var ctx = new AnnotationConfigApplicationContext(UserLogConfig.class, LoggingAutoConfig.class)) {
            assertEquals(List.of("memoryLogSink"), List.of(ctx.getBeanNamesForType(LogSink.class)),
                    "利用者の LogSink があるときは logSink() を登録しないでください。"
                            + "@Conditional(OnMissingLogSinkCondition.class) と matches の判定を確認してください");
            assertInstanceOf(MemoryLogSink.class, ctx.getBean(AccessLogger.class).sink(),
                    "AccessLogger には利用者の MemoryLogSink が渡されるはずです");
        }
    }

    @Test
    @DisplayName("クラスとして直接登録した LogSink でも、既定は引っ込む(型で判定している)")
    void backsOffForRegisteredClass() {
        try (var ctx = new AnnotationConfigApplicationContext(MemoryLogSink.class, LoggingAutoConfig.class)) {
            assertEquals(1, ctx.getBeansOfType(LogSink.class).size(),
                    "LogSink 型の Bean は1つだけのはずです(Bean 名ではなく型で判定しているか確認してください)");
            assertInstanceOf(MemoryLogSink.class, ctx.getBean(AccessLogger.class).sink(),
                    "AccessLogger には登録済みの MemoryLogSink が渡されるはずです");
        }
    }

    @Test
    @DisplayName("アクセスログが「メソッド パス ステータス」の形で出力先に書かれる")
    void writesAccessLog() {
        try (var ctx = new AnnotationConfigApplicationContext(UserLogConfig.class, LoggingAutoConfig.class)) {
            AccessLogger logger = ctx.getBean(AccessLogger.class);
            logger.log("GET", "/orders", 200);
            logger.log("POST", "/orders", 400);
            MemoryLogSink sink = (MemoryLogSink) ctx.getBean(LogSink.class);
            assertEquals(List.of("GET /orders 200", "POST /orders 400"), sink.lines(),
                    "log は method + \" \" + path + \" \" + status の1行を sink.write に渡してください");
        }
    }
}
