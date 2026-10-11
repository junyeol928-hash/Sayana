import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MorningGreetingTest {

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void captureStdout() {
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    /** main を実行して、標準出力に出た文字列をそのまま返す */
    private String runMain() {
        MorningGreeting.main(new String[0]);
        System.out.flush();
        return captured.toString(StandardCharsets.UTF_8);
    }

    /** 出力を行ごとに分割する(Windows の \r\n にも対応) */
    private String[] lines() {
        String out = runMain();
        if (out.isEmpty()) {
            return new String[0];
        }
        return out.split("\\R", -1);
    }

    @Test
    @DisplayName("ちょうど3行出力されている")
    void printsThreeLines() {
        String out = runMain();
        assertFalse(out.isEmpty(), "何も出力されていません。System.out.println で3行出力してください");
        String[] ls = out.split("\\R");
        assertEquals(3, ls.length,
                "出力が " + ls.length + " 行になっています。println を3回使って、ちょうど3行にしてください(print だと改行されません)");
    }

    @Test
    @DisplayName("1行目が「おはようございます。」")
    void firstLine() {
        String[] ls = lines();
        assertTrue(ls.length >= 1, "何も出力されていません");
        assertEquals("おはようございます。", ls[0],
                "1行目が違います。「おはようございます。」(句点は全角の「。」)と完全に一致させてください");
    }

    @Test
    @DisplayName("2行目が自己紹介になっている")
    void secondLine() {
        String[] ls = lines();
        assertTrue(ls.length >= 2, "2行目がありません。1行ずつ println で出力しているか確認してください");
        assertEquals("本日から開発部に配属されたハルです。", ls[1],
                "2行目が違います。「本日から開発部に配属されたハルです。」と完全に一致させてください(余計な空白に注意)");
    }

    @Test
    @DisplayName("3行目が「よろしくお願いします。」")
    void thirdLine() {
        String[] ls = lines();
        assertTrue(ls.length >= 3, "3行目がありません。1行ずつ println で出力しているか確認してください");
        assertEquals("よろしくお願いします。", ls[2],
                "3行目が違います。「よろしくお願いします。」と完全に一致させてください");
    }

    @Test
    @DisplayName("最後の行も改行で終わっている(println を使っている)")
    void endsWithNewline() {
        String out = runMain();
        assertFalse(out.isEmpty(), "何も出力されていません");
        assertTrue(out.endsWith("\n"),
                "最後が改行で終わっていません。3行目も print ではなく println で出力してください");
    }
}
