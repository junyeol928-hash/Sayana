import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class NameTagTest {

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

    /** printNameTag を呼んで、出力を行ごとに返す(Windows の \r\n にも対応) */
    private String[] tagLines(String name) {
        NameTag.printNameTag(name);
        System.out.flush();
        String out = captured.toString(StandardCharsets.UTF_8);
        assertFalse(out.isEmpty(), "何も出力されていません。System.out.println で3行出力してください");
        assertTrue(out.endsWith("\n"), "最後の行が改行で終わっていません。3行目も println で出力してください");
        String[] lines = out.split("\\R");
        assertEquals(3, lines.length,
                "出力が " + lines.length + " 行になっています。罫線・名前・罫線のちょうど3行にしてください");
        return lines;
    }

    @Test
    @DisplayName("基本形:Haru の名札")
    void haru() {
        String[] ls = tagLines("Haru");
        assertEquals("+------+", ls[0], "1行目の罫線が違います。\"+\" と、名前の文字数+2 個の \"-\" と、\"+\" をつなげてください");
        assertEquals("| Haru |", ls[1], "2行目が違います。\"| \" + 名前 + \" |\" の形にしてください(縦棒の内側に半角スペース1つ)");
        assertEquals("+------+", ls[2], "3行目の罫線が違います。1行目と同じ罫線を出力してください");
    }

    @Test
    @DisplayName("スペースを含む長めの名前でも幅がそろう")
    void longName() {
        String[] ls = tagLines("Taro Yamada");
        assertEquals("+-------------+", ls[0], "罫線の長さが名前に合っていません。\"-\" の個数は name.length() + 2 です(固定の長さにしていませんか?)");
        assertEquals("| Taro Yamada |", ls[1], "2行目が違います。名前の途中のスペースもそのまま出力してください");
        assertEquals("+-------------+", ls[2], "3行目の罫線が1行目と同じになっていません");
    }

    @Test
    @DisplayName("境界値:1文字の名前")
    void oneChar() {
        String[] ls = tagLines("A");
        assertEquals("+---+", ls[0], "1文字のときは \"-\" が3個(1 + 2)になるはずです");
        assertEquals("| A |", ls[1], "2行目が違います");
        assertEquals("+---+", ls[2], "3行目の罫線が違います");
    }

    @Test
    @DisplayName("境界値:空文字(名前未入力)でも枠だけは崩れない")
    void emptyName() {
        String[] ls = tagLines("");
        assertEquals("+--+", ls[0], "名前が空のときは \"-\" が2個(0 + 2)になるはずです");
        assertEquals("|  |", ls[1], "名前が空のときの2行目は \"|  |\"(縦棒の間に半角スペース2つ)になるはずです");
        assertEquals("+--+", ls[2], "3行目の罫線が違います");
    }

    @Test
    @DisplayName("3行とも同じ幅になっている")
    void sameWidth() {
        String[] ls = tagLines("Java Dojo 2026");
        assertEquals(ls[0].length(), ls[1].length(),
                "1行目と2行目の幅(文字数)がそろっていません。\"-\" の個数を name.length() + 2 にしてください");
        assertEquals(ls[0], ls[2], "1行目と3行目の罫線が同じになっていません");
    }
}
