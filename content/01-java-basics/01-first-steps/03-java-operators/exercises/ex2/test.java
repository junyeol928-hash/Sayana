import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LeapYearTest {

    @Test
    @DisplayName("4で割り切れる年(2024年)はうるう年")
    void divisibleBy4() {
        assertTrue(LeapYear.isLeapYear(2024), "2024年は4で割り切れるのでうるう年(true)です");
    }

    @Test
    @DisplayName("4で割り切れない年(2023年)は平年")
    void notDivisibleBy4() {
        assertFalse(LeapYear.isLeapYear(2023), "2023年は4で割り切れないので平年(false)です");
    }

    @Test
    @DisplayName("100で割り切れて400で割り切れない年(1900年・2100年)は平年")
    void divisibleBy100() {
        assertFalse(LeapYear.isLeapYear(1900),
                "1900年は100で割り切れるので平年(false)です。『100で割り切れたら除外』の条件が入っていますか?");
        assertFalse(LeapYear.isLeapYear(2100),
                "2100年は100で割り切れるので平年(false)です。『100で割り切れたら除外』の条件が入っていますか?");
    }

    @Test
    @DisplayName("400で割り切れる年(2000年・2400年)はうるう年")
    void divisibleBy400() {
        assertTrue(LeapYear.isLeapYear(2000),
                "2000年は400で割り切れるのでうるう年(true)です。『400で割り切れたらうるう年』の条件を || でつないでいますか?");
        assertTrue(LeapYear.isLeapYear(2400),
                "2400年は400で割り切れるのでうるう年(true)です");
    }

    @Test
    @DisplayName("4で割り切れる普通の年(1996年)と直前の年(1999年)")
    void nearBoundary() {
        assertTrue(LeapYear.isLeapYear(1996), "1996年は4で割り切れ、100では割り切れないのでうるう年(true)です");
        assertFalse(LeapYear.isLeapYear(1999), "1999年は4で割り切れないので平年(false)です");
    }
}
