import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class LogCounter {

    /** log の中で [ERROR] を含む行の数を返す */
    public static long countErrors(Path log) throws IOException {
        try (Stream<String> lines = Files.lines(log)) {
            return lines.filter(line -> line.contains("[ERROR]"))
                        .count();
        }
    }

    public static void main(String[] args) throws IOException {
        Path log = Files.createTempFile("app", ".log");
        Files.write(log, List.of(
                "2026-10-01 09:00:01 [INFO] 起動しました",
                "2026-10-01 09:00:05 [ERROR] DB接続に失敗",
                "2026-10-01 09:00:06 [WARN] ERROR率が上昇中",
                "2026-10-01 09:00:09 [ERROR] タイムアウト"));
        System.out.println(countErrors(log)); // 2
        Files.delete(log);
    }
}
