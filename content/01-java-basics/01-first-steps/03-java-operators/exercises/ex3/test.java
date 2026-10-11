import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BmiCheckerTest {

    private static final double DELTA = 1e-9;

    @Test
    @DisplayName("170cm・65kg の BMI は 22.5(小数第2位以下を四捨五入して切り上がる)")
    void bmiRoundUp() {
        assertEquals(22.5, BmiChecker.bmi(170, 65.0), DELTA,
                "170cm・65kg の BMI は 22.5 です。22.0 になるなら Math.round(x * 10) / 10 と整数で割っていないか、"
                + "65.0 になるなら heightCm / 100 が整数除算(170 / 100 = 1)になっていないか確認してください");
    }

    @Test
    @DisplayName("160cm・50kg の BMI は 19.5(四捨五入で切り捨てられる)")
    void bmiRoundDown() {
        assertEquals(19.5, BmiChecker.bmi(160, 50.0), DELTA,
                "160cm・50kg の BMI は 19.5 です(計算値は 19.53…)。小数第1位まで四捨五入していますか?");
    }

    @Test
    @DisplayName("180cm・90kg の BMI は 27.8")
    void bmiRepeating() {
        assertEquals(27.8, BmiChecker.bmi(180, 90.0), DELTA,
                "180cm・90kg の BMI は 27.8 です(計算値は 27.77…)");
    }

    @Test
    @DisplayName("判定の境界: 18.4 は低体重、18.5 は普通体重")
    void judgeLowerBoundary() {
        assertEquals("低体重", BmiChecker.judge(18.4), "18.5 未満は \"低体重\" です");
        assertEquals("普通体重", BmiChecker.judge(18.5),
                "18.5 ちょうどは \"普通体重\" です。< と <= を取り違えていないか確認してください");
    }

    @Test
    @DisplayName("判定の境界: 24.9 は普通体重、25.0 は肥満")
    void judgeUpperBoundary() {
        assertEquals("普通体重", BmiChecker.judge(24.9), "25.0 未満は \"普通体重\" です");
        assertEquals("肥満", BmiChecker.judge(25.0),
                "25.0 ちょうどは \"肥満\" です。< と <= を取り違えていないか確認してください");
    }

    @Test
    @DisplayName("計算から判定まで: 175cm・56.6kg は BMI 18.5 で普通体重")
    void bmiThenJudge() {
        double value = BmiChecker.bmi(175, 56.6);
        assertEquals(18.5, value, DELTA, "175cm・56.6kg の BMI は 18.5 です(計算値 18.48… を四捨五入)");
        assertEquals("普通体重", BmiChecker.judge(value), "BMI 18.5 の判定は \"普通体重\" です");
    }
}
