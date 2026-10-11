import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QuantityParserTest {

    @Test
    @DisplayName("数字ならそのまま int に変換する(マイナスも)")
    void normal() {
        assertEquals(12, QuantityParser.parseOrDefault("12", 1),
                "\"12\" は 12 に変換してください");
        assertEquals(-3, QuantityParser.parseOrDefault("-3", 1),
                "\"-3\" は -3 に変換してください(範囲チェックはこのメソッドの役目ではありません)");
    }

    @Test
    @DisplayName("前後の空白は取り除いてから変換する")
    void strip() {
        assertEquals(7, QuantityParser.parseOrDefault(" 7 ", 1),
                "\" 7 \" は 7 になります。parseInt の前に strip() で前後の空白を除いてください");
    }

    @Test
    @DisplayName("数字でない文字列・空文字は既定値")
    void notNumber() {
        assertEquals(1, QuantityParser.parseOrDefault("12a", 1),
                "変換できない文字列のときは NumberFormatException を catch して既定値を返してください");
        assertEquals(5, QuantityParser.parseOrDefault("", 5),
                "空文字のときも既定値を返してください");
    }

    @Test
    @DisplayName("null でも例外にならず既定値")
    void nullText() {
        assertDoesNotThrow(() -> QuantityParser.parseOrDefault(null, 1),
                "text が null で例外が出ています。strip() を呼ぶ前に null を if ではじいてください");
        assertEquals(1, QuantityParser.parseOrDefault(null, 1),
                "text が null のときは既定値を返してください");
    }

    @Test
    @DisplayName("int の最大値 2147483647 は変換でき、1 つ超えると既定値(境界値)")
    void intRange() {
        assertEquals(2147483647, QuantityParser.parseOrDefault("2147483647", 1),
                "int の最大値 2147483647 は変換できます");
        assertEquals(1, QuantityParser.parseOrDefault("2147483648", 1),
                "int に収まらない数字は parseInt が NumberFormatException を投げるので既定値になります");
    }
}
