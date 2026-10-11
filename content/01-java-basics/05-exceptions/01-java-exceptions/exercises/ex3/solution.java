// 開いた・閉じたを log に記録するリソース
class TrackedResource implements AutoCloseable {
    private final String name;
    private final StringBuilder log;

    TrackedResource(String name, StringBuilder log) {
        this.name = name;
        this.log = log;
        log.append("open:").append(name).append(";");
    }

    @Override
    public void close() {
        log.append("close:").append(name).append(";");
    }
}

// 設定エラーを表す非検査例外(完成済み)
class BatchConfigException extends RuntimeException {
    BatchConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class BatchJob {

    static String run(boolean fail) {
        StringBuilder log = new StringBuilder();
        try (TrackedResource db = new TrackedResource("DB", log);
             TrackedResource file = new TrackedResource("FILE", log)) {
            log.append("work;");
            if (fail) {
                throw new IllegalStateException("書き込み失敗");
            }
        } catch (IllegalStateException e) {
            log.append("catch:").append(e.getMessage()).append(";");
        } finally {
            log.append("finally;");
        }
        return log.toString();
    }

    static int readLimit(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new BatchConfigException("件数の設定が不正: " + text, e);
        }
    }

    public static void main(String[] args) {
        System.out.println(run(false));
        System.out.println(run(true));
        System.out.println(readLimit("500"));
    }
}
