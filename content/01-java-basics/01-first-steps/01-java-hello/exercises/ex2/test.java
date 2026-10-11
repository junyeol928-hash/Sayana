import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SelfIntroTest {

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

    @Test
    @DisplayName("基本形:山田さん・開発部・3年")
    void basic() {
        assertEquals("はじめまして。開発部の山田です。Java歴は3年です。",
                SelfIntro.buildIntro("山田", "開発部", 3),
                "形式は「はじめまして。{所属}の{名前}です。Java歴は{年数}年です。」です。所属と名前の順番、句点「。」の位置を確認してください");
    }

    @Test
    @DisplayName("別の値でも引数がそのまま使われる(固定の文字列を返していない)")
    void otherValues() {
        assertEquals("はじめまして。品質保証部のSatoです。Java歴は12年です。",
                SelfIntro.buildIntro("Sato", "品質保証部", 12),
                "引数の name・team・years を + でつないでいますか?例の文字列をそのまま返していないか確認してください");
    }

    @Test
    @DisplayName("境界値:Java歴0年でも「0年」と表示される")
    void zeroYears() {
        assertEquals("はじめまして。新人研修チームのハルです。Java歴は0年です。",
                SelfIntro.buildIntro("ハル", "新人研修チーム", 0),
                "years が 0 のときも「Java歴は0年です。」になるはずです。years をそのまま + でつないでください");
    }

    @Test
    @DisplayName("前後や途中に余計な空白が入っていない")
    void noExtraSpaces() {
        String result = SelfIntro.buildIntro("田中", "営業部", 5);
        assertNotNull(result, "null が返っています。組み立てた文字列を return してください");
        assertFalse(result.contains(" ") || result.contains("　"),
                "空白(半角または全角スペース)が入っています: [" + result + "]。文字列リテラルの中の空白を取り除いてください");
    }

    @Test
    @DisplayName("main で buildIntro(\"ハル\", \"開発部\", 1) の結果が1行表示される")
    void mainPrintsIntro() {
        SelfIntro.main(new String[0]);
        System.out.flush();
        String out = captured.toString(StandardCharsets.UTF_8);
        assertFalse(out.isEmpty(), "main で何も出力されていません。System.out.println(buildIntro(\"ハル\", \"開発部\", 1)); を書いてください");
        assertEquals("はじめまして。開発部のハルです。Java歴は1年です。", out.strip(),
                "main の出力が違います。buildIntro(\"ハル\", \"開発部\", 1) の結果を println で表示してください");
        assertTrue(out.endsWith("\n"), "出力が改行で終わっていません。print ではなく println を使ってください");
    }
}
