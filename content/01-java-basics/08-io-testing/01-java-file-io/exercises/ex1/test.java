import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogCounterTest {

    @TempDir
    Path dir; // テストごとに作られ、終わると自動で消える一時フォルダ

    private Path log(String... lines) throws IOException {
        Path p = dir.resolve("app.log");
        Files.write(p, List.of(lines), StandardCharsets.UTF_8);
        return p;
    }

    @Test
    @DisplayName("[ERROR] の行だけを数える(問題文の例)")
    void basic() throws IOException {
        Path p = log(
                "2026-10-01 09:00:01 [INFO] 起動しました",
                "2026-10-01 09:00:05 [ERROR] DB接続に失敗",
                "2026-10-01 09:00:06 [WARN] ERROR率が上昇中",
                "2026-10-01 09:00:09 [ERROR] タイムアウト");
        assertEquals(2, LogCounter.countErrors(p),
                "[ERROR] を含む行は2行です。ファイルを読んで \"[ERROR]\" を含む行を数えていますか?");
    }

    @Test
    @DisplayName("メッセージに ERROR という単語があるだけの WARN 行は数えない")
    void warnWithErrorWord() throws IOException {
        Path p = log(
                "2026-10-01 09:00:06 [WARN] ERROR率が上昇中",
                "2026-10-01 09:00:07 [INFO] ERRORログを圧縮しました");
        assertEquals(0, LogCounter.countErrors(p),
                "ERROR という単語を含むだけの行は数えません。\"ERROR\" ではなく \"[ERROR]\" で判定してください");
    }

    @Test
    @DisplayName("空のファイルなら 0 件")
    void empty() throws IOException {
        Path p = dir.resolve("empty.log");
        Files.createFile(p);
        assertEquals(0, LogCounter.countErrors(p), "空のファイルなら 0 を返してください");
    }

    @Test
    @DisplayName("Windows の改行(CRLF)で 1000 行あるログも数えられる")
    void manyLinesCrlf() throws IOException {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            String level = (i % 10 == 0) ? "[ERROR]" : "[INFO]";
            lines.add("2026-10-01 10:00:00 " + level + " 処理 " + i);
        }
        Path p = dir.resolve("big.log");
        Files.writeString(p, String.join("\r\n", lines) + "\r\n", StandardCharsets.UTF_8);
        assertEquals(100, LogCounter.countErrors(p),
                "1000 行中 [ERROR] は 100 行です。最初や最後の行だけ読んでいませんか?");
    }

    @Test
    @DisplayName("ファイルが存在しなければ IOException を投げる(握りつぶさない)")
    void missingFile() {
        Path p = dir.resolve("no-such.log");
        assertThrows(IOException.class, () -> LogCounter.countErrors(p),
                "存在しないファイルを渡したら IOException(NoSuchFileException)を投げてください。catch して 0 を返すと、障害に気づけません");
    }
}
