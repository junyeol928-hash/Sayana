import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaxCalcTest {

    @Test
    @DisplayName("税額:標準10%・軽減8%・非課税0%")
    void taxOf() {
        assertEquals(100, TaxCategory.STANDARD.taxOf(1000),
                "STANDARD(10%)で 1000 円の税額は 100 円です。taxOf で ratePercent を使っていますか?");
        assertEquals(80, TaxCategory.REDUCED.taxOf(1000),
                "REDUCED(8%)で 1000 円の税額は 80 円です");
        assertEquals(0, TaxCategory.EXEMPT.taxOf(1000),
                "EXEMPT(0%)の税額は 0 円です");
    }

    @Test
    @DisplayName("1円未満は切り捨て(99円の8%は7円)、0円の税額は0円")
    void roundDown() {
        assertEquals(7, TaxCategory.REDUCED.taxOf(99),
                "99 × 8 / 100 = 7.92 なので、切り捨てて 7 円です");
        assertEquals(0, TaxCategory.STANDARD.taxOf(0),
                "0 円の税額は 0 円です");
    }

    @Test
    @DisplayName("税込価格 = 本体価格 + 税額")
    void priceWithTax() {
        assertEquals(1080, TaxCalc.priceWithTax(1000, TaxCategory.REDUCED),
                "1000 円・軽減税率の税込価格は 1080 円です。税額だけを返していませんか?");
        assertEquals(1100, TaxCalc.priceWithTax(1000, TaxCategory.STANDARD),
                "1000 円・標準税率の税込価格は 1100 円です");
        assertEquals(500, TaxCalc.priceWithTax(500, TaxCategory.EXEMPT),
                "非課税なら税込価格は本体価格のままです");
    }

    @Test
    @DisplayName("parse:前後の空白と大文字小文字を吸収して変換できる")
    void parseNormalized() {
        assertEquals(TaxCategory.REDUCED, TaxCalc.parse(" reduced "),
                "\" reduced \" は REDUCED に変換してください。strip() と toUpperCase() で整えてから valueOf します");
        assertEquals(TaxCategory.STANDARD, TaxCalc.parse("STANDARD"),
                "\"STANDARD\" は STANDARD に変換してください");
    }

    @Test
    @DisplayName("parse:知らない文字列や null は IllegalArgumentException")
    void parseInvalid() {
        assertThrows(IllegalArgumentException.class, () -> TaxCalc.parse("food"),
                "どの定数にも当てはまらない文字列は IllegalArgumentException にしてください");
        assertThrows(IllegalArgumentException.class, () -> TaxCalc.parse(""),
                "空文字も IllegalArgumentException にしてください");
        assertThrows(IllegalArgumentException.class, () -> TaxCalc.parse(null),
                "null は IllegalArgumentException にしてください(valueOf に null を渡すと NullPointerException になります)");
    }
}
