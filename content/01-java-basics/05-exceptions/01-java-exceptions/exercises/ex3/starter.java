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
        // TODO: "close:名前;" を log に追記する
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
        // TODO: try-with-resources で "DB" → "FILE" の順に TrackedResource を開く
        // TODO: ブロック内で "work;" を記録し、fail なら IllegalStateException("書き込み失敗") を投げる
        // TODO: IllegalStateException を catch したら "catch:" + メッセージ + ";" を記録
        // TODO: finally で "finally;" を記録
        return log.toString();
    }

    static int readLimit(String text) {
        // TODO: Integer.parseInt(text) を返す
        // TODO: NumberFormatException は cause を渡して BatchConfigException にラップする
        return 0;
    }

    public static void main(String[] args) {
        System.out.println(run(false));
        System.out.println(run(true));
        System.out.println(readLimit("500"));
    }
}
