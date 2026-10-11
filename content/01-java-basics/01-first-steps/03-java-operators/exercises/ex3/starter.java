public class BmiChecker {

    // BMI を小数第1位まで四捨五入して返す
    public static double bmi(int heightCm, double weightKg) {
        // TODO: ここに書く
        // 1. heightCm をメートルに直す(int 同士の割り算に注意)
        // 2. 体重 ÷ (身長 × 身長) を計算する
        // 3. Math.round を使って小数第1位まで四捨五入する
        return 0.0;
    }

    // BMI から判定結果("低体重" / "普通体重" / "肥満")を返す
    public static String judge(double bmi) {
        // TODO: ここに書く(三項演算子 ? : を使う)
        return "";
    }

    public static void main(String[] args) {
        double value = bmi(170, 65.0);
        System.out.println("BMI: " + value + " → " + judge(value));
    }
}
