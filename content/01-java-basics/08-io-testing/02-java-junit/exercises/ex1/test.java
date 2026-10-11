import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PointCalculatorTest {

    @Test
    @DisplayName("100円ちょうどで1ポイント、99円は0ポイント(境界値)")
    void boundary100() {
        assertAll(
                () -> assertEquals(1, PointCalculator.points(100, false),
                        "100 円で 1 ポイントです"),
                () -> assertEquals(0, PointCalculator.points(99, false),
                        "99 円は 100 円未満なので 0 ポイントです。四捨五入になっていませんか?"));
    }

    @Test
    @DisplayName("199円は1ポイント(端数は切り捨て)")
    void roundDown() {
        assertEquals(1, PointCalculator.points(199, false),
                "199 円は 1 ポイントです(端数は切り捨て)。Math.round は四捨五入なので、int 同士の割り算 amount / 100 を使いましょう");
    }

    @Test
    @DisplayName("誕生月は2倍(250円 → 2ポイントの2倍で4ポイント)")
    void birthday() {
        assertEquals(4, PointCalculator.points(250, true),
                "250 円は 2 ポイント(端数切り捨て)、誕生月はその 2 倍で 4 ポイントです");
    }

    @Test
    @DisplayName("返品(マイナス金額)は0ポイント")
    void negative() {
        assertEquals(0, PointCalculator.points(-500, false),
                "金額がマイナスのときは 0 ポイントです。マイナスのポイントを返さないよう、先に amount <= 0 をはじいてください");
        assertEquals(0, PointCalculator.points(-500, true),
                "誕生月でも、金額がマイナスなら 0 ポイントです");
    }

    @Test
    @DisplayName("0円は0ポイント(境界値)")
    void zero() {
        assertEquals(0, PointCalculator.points(0, true), "0 円は 0 ポイントです");
    }
}
