import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParkingFeeTest {

    @ParameterizedTest(name = "{0}分 → {1}円")
    @CsvSource({
        "0,    0",
        "30,   0",
        "31,   200",
        "60,   200",
        "61,   400",
        "240,  1400",
        "241,  1500",
        "1440, 1500"
    })
    @DisplayName("駐車時間ごとの料金")
    void fee(int minutes, int expected) {
        assertEquals(expected, ParkingFee.fee(minutes),
                minutes + " 分の料金は " + expected + " 円です。"
                        + "最初の30分は無料、超えた分は30分ごと(切り上げ)に200円、上限1500円を確認してください");
    }

    @Test
    @DisplayName("30分ちょうどは無料、31分から200円(無料枠の境界)")
    void freeBoundary() {
        assertEquals(0, ParkingFee.fee(30), "30 分ちょうどはまだ無料です。< と <= を確認してください");
        assertEquals(200, ParkingFee.fee(31), "31 分は 30 分を1分超えたので、切り上げて 200 円です");
    }

    @Test
    @DisplayName("上限は1500円(何時間停めても)")
    void cap() {
        assertEquals(1500, ParkingFee.fee(24 * 60 * 3),
                "3日停めても上限の 1500 円です。Math.min で上限をかけていますか?");
    }

    @Test
    @DisplayName("マイナスの時間は IllegalArgumentException")
    void negative() {
        assertThrows(IllegalArgumentException.class, () -> ParkingFee.fee(-1),
                "駐車時間が負のときは IllegalArgumentException を投げてください");
    }
}
