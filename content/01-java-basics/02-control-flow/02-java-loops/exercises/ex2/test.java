import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PyramidTest {

    @Test
    @DisplayName("高さ1なら * が1個だけ")
    void heightOne() {
        assertEquals("*", Pyramid.build(1),
                "高さ1なら \"*\" だけになるはずです。スペースの個数(height - i)と * の個数(2 * i - 1)を確認しましょう");
    }

    @Test
    @DisplayName("高さ3のピラミッド")
    void heightThree() {
        assertEquals("  *\n ***\n*****", Pyramid.build(3),
                "1段目はスペース2個+*1個、2段目はスペース1個+*3個、3段目は*5個です。行は \\n で区切ります");
    }

    @Test
    @DisplayName("高さ5のピラミッドの1段目と最下段")
    void heightFive() {
        String result = Pyramid.build(5);
        assertTrue(result.startsWith("    *\n"),
                "高さ5の1段目は「スペース4個 + * 1個」です。スペースのループ回数を確認しましょう");
        assertTrue(result.endsWith("\n*********"),
                "高さ5の最下段は * が9個(2 * 5 - 1)で、先頭にスペースはありません");
        assertEquals(5, result.split("\n").length,
                "高さ5なら5行になるはずです。外側ループの回数(off-by-one)を確認しましょう");
    }

    @Test
    @DisplayName("行末にスペースや改行を付けない")
    void noTrailingSpaceOrNewline() {
        String result = Pyramid.build(4);
        assertFalse(result.endsWith("\n"),
                "末尾に改行が付いています。区切りの \\n は「2段目以降の前に」付けましょう");
        assertFalse(result.contains(" \n") || result.endsWith(" "),
                "* の後ろにスペースが付いています。スペースは * の前(左側)にだけ並べます");
    }

    @Test
    @DisplayName("高さが0以下なら空文字列")
    void zeroOrNegative() {
        assertEquals("", Pyramid.build(0), "高さ0のときは空文字列 \"\" を返してください");
        assertEquals("", Pyramid.build(-2), "高さが負のときも空文字列 \"\" を返してください");
    }
}
