import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SavingsPlanTest {

    private static int call(long principal, int ratePercent, long target) {
        return assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> SavingsPlan.yearsToReach(principal, ratePercent, target),
                "2秒以内に終わりませんでした。無限ループになっていないか、利息0円のときに -1 を返しているか確認しましょう");
    }

    @Test
    @DisplayName("100万円・年利5%で120万円に届くのは4年後")
    void typical() {
        assertEquals(4, call(1_000_000, 5, 1_200_000),
                "105万 → 110.25万 → 115.7625万 → 121.5506万 で4年後です。毎年の利息を残高に加えているか確認しましょう");
    }

    @Test
    @DisplayName("ちょうど目標額に到達した年で止まる(以上で判定)")
    void exactlyReached() {
        assertEquals(3, call(100, 100, 800),
                "200 → 400 → 800 と、3年目でちょうど800円になります。ループ条件は「残高 < 目標」です(<= にしていませんか?)");
        assertEquals(4, call(100, 100, 801),
                "801円には 800 → 1600 の4年目で届きます");
    }

    @Test
    @DisplayName("最初から目標額以上なら0年")
    void alreadyReached() {
        assertEquals(0, call(500_000, 3, 500_000),
                "元本が目標額と同じなら0年です。ループに入る前に条件を判定する while 文なら自然にそうなります");
        assertEquals(0, call(800_000, 3, 500_000),
                "元本が目標額より多いときも0年です");
    }

    @Test
    @DisplayName("年利0%なら届かないので -1")
    void zeroRate() {
        assertEquals(-1, call(1_000_000, 0, 2_000_000),
                "年利0%では残高が増えないので -1 を返してください");
    }

    @Test
    @DisplayName("利息が切り捨てで0円になるなら -1")
    void interestTruncatedToZero() {
        assertEquals(-1, call(10, 5, 100),
                "10円 × 5% = 0.5円 → 切り捨てで0円。残高が増えないので -1 を返してください");
    }

    @Test
    @DisplayName("年利1%で倍になるのは70年後(長期の計算)")
    void longTerm() {
        assertEquals(70, call(1_000_000, 1, 2_000_000),
                "100万円を年利1%(毎年1円未満切り捨て)で200万円にするには70年かかります。利息の計算順(残高 × 年利 ÷ 100)を確認しましょう");
    }
}
