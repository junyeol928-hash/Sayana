import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @TempDir
    Path dir; // テストごとに作られ、終わると自動で消える一時フォルダ

    private Path file(String... lines) throws IOException {
        Path p = dir.resolve("app.properties");
        Files.write(p, List.of(lines), StandardCharsets.UTF_8);
        return p;
    }

    @Test
    @DisplayName("key=value を読み込み、前後の空白を取り除く")
    void basic() throws IOException {
        Map<String, String> c = ConfigLoader.load(file(
                "input.dir = C:/data/in",
                "output.dir=C:/data/out",
                "app.title = Java道場"));
        assertEquals(Map.of("input.dir", "C:/data/in", "output.dir", "C:/data/out",
                        "app.title", "Java道場"), c,
                "キーと値を = で分け、それぞれ trim() して Map に入れてください。日本語の値も UTF-8 で読めているか確認しましょう");
    }

    @Test
    @DisplayName("空行・空白だけの行・# のコメント行は無視する")
    void skipsCommentsAndBlankLines() throws IOException {
        Map<String, String> c = ConfigLoader.load(file(
                "# 売上集計バッチの設定",
                "",
                "   ",
                "   # インデントされたコメント",
                "mode=daily"));
        assertEquals(Map.of("mode", "daily"), c,
                "空行・空白だけの行・(前後の空白を除いて)# で始まる行は読み飛ばしてください");
    }

    @Test
    @DisplayName("最初の = で分ける(値の中の = は残す)")
    void splitAtFirstEquals() throws IOException {
        Map<String, String> c = ConfigLoader.load(file(
                "db.url=jdbc:postgresql://localhost/shop?ssl=true&a=b"));
        assertEquals("jdbc:postgresql://localhost/shop?ssl=true&a=b", c.get("db.url"),
                "値の中の = で切れています。indexOf('=') で最初の = の位置を探すか、split(\"=\", 2) を使ってください");
    }

    @Test
    @DisplayName("同じキーが2回あれば、後に書かれた方が勝つ")
    void lastOneWins() throws IOException {
        Map<String, String> c = ConfigLoader.load(file(
                "threads=4",
                "threads=8"));
        assertEquals("8", c.get("threads"),
                "同じキーは後に書かれた値で上書きしてください(Map.put は上書きします。putIfAbsent になっていませんか?)");
    }

    @Test
    @DisplayName("ファイルが存在しなければ、例外にせず空の Map を返す")
    void missingFile() throws IOException {
        Map<String, String> c = ConfigLoader.load(dir.resolve("no-such.properties"));
        assertNotNull(c, "ファイルが無いときも null ではなく空の Map を返してください");
        assertTrue(c.isEmpty(), "ファイルが無いときは空の Map を返してください(Files.exists で先に確認)");
    }

    @Test
    @DisplayName("= の無い行があれば、行番号つきで IllegalArgumentException")
    void invalidLine() throws IOException {
        Path p = file(
                "# コメント",
                "mode=daily",
                "threads 8",
                "retry=3");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ConfigLoader.load(p),
                "= を含まない行があったら IllegalArgumentException を投げてください");
        assertNotNull(e.getMessage(), "例外のメッセージに「3行目」のように行番号を入れてください");
        assertTrue(e.getMessage().contains("3行目"),
                "例外のメッセージに「3行目」を含めてください(コメント行も1行と数え、1から数えます)。実際のメッセージ: " + e.getMessage());
    }
}
