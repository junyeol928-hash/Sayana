public class BmiChecker {

    // BMI を小数第1位まで四捨五入して返す
    public static double bmi(int heightCm, double weightKg) {
        // 100 ではなく 100.0 で割る(int / int だと 170 / 100 = 1 になってしまう)
        double heightM = heightCm / 100.0;
        double raw = weightKg / (heightM * heightM);
        // 10 倍して四捨五入し、10.0 で割って小数第1位に戻す
        return Math.round(raw * 10) / 10.0;
    }

    // BMI から判定結果("低体重" / "普通体重" / "肥満")を返す
    public static String judge(double bmi) {
        return bmi < 18.5 ? "低体重" : (bmi < 25.0 ? "普通体重" : "肥満");
    }

    public static void main(String[] args) {
        double value = bmi(170, 65.0);
        System.out.println("BMI: " + value + " → " + judge(value));
    }
}
