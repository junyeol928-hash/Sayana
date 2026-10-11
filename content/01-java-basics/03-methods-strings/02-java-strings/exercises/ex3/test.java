import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportLineTest {

    @Test
    @DisplayName("padRight は右側を空白で埋め、長ければ切り詰める(幅ちょうどは境界値)")
    void padRight() {
        assertEquals("abc   ", ReportLine.padRight("abc", 6), "\"abc\" を幅6にすると右に空白3つで \"abc   \" です");
        assertEquals("abcdef", ReportLine.padRight("abcdef", 6), "ちょうど幅6ならそのままです");
        assertEquals("abcdef", ReportLine.padRight("abcdefg", 6),
                "幅より長いときは先頭 width 文字に切り詰めてください(substring(0, width))");
    }

    @Test
    @DisplayName("padLeft は左側を空白で埋め、長くても切り詰めない")
    void padLeft() {
        assertEquals("   42", ReportLine.padLeft("42", 5), "\"42\" を幅5にすると左に空白3つで \"   42\" です");
        assertEquals("12345", ReportLine.padLeft("12345", 5), "ちょうど幅5ならそのままです");
        assertEquals("123456", ReportLine.padLeft("123456", 4),
                "padLeft は数値用なので、幅より長くても切り詰めずにそのまま返してください");
    }

    @Test
    @DisplayName("null は空文字として扱い、幅いっぱいの空白になる")
    void nullInput() {
        assertEquals("   ", ReportLine.padRight(null, 3), "null は \"\" として扱い、空白3つの \"   \" を返してください");
        assertEquals("   ", ReportLine.padLeft(null, 3), "null は \"\" として扱い、空白3つの \"   \" を返してください");
    }

    @Test
    @DisplayName("formatLine は 商品名12桁・数量5桁・金額11桁(カンマ区切り)で1行を作る")
    void formatLine() {
        String line = ReportLine.formatLine("Coffee", 3, 3600);
        assertEquals("Coffee          3      3,600", line,
                "商品名を左寄せ12桁、数量を右寄せ5桁、金額を 3,600 の形にして右寄せ11桁でつなげてください");
        assertEquals(28, line.length(), "1行の長さは 12 + 5 + 11 = 28 文字になるはずです");
        assertEquals("Mug            12  1,234,567", ReportLine.formatLine("Mug", 12, 1234567),
                "金額 1234567 は \"1,234,567\" と3桁区切りにします。String.format(\"%,d\", amount) を使いましょう");
    }

    @Test
    @DisplayName("formatLine で長い商品名は12桁に切り詰めて列をそろえる")
    void formatLineLongName() {
        String line = ReportLine.formatLine("PremiumCoffeeBeans", 1, 980);
        assertEquals("PremiumCoffe    1        980", line,
                "商品名が12文字を超えたら \"PremiumCoffe\" のように切り詰めます。padRight を使っていますか?");
        assertEquals(28, line.length(), "長い商品名でも1行は28文字にそろえてください");
    }
}
