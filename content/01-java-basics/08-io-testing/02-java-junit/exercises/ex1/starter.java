public class PointCalculator {

    /** 購入金額からポイントを計算する(100円で1ポイント、誕生月は2倍) */
    public static int points(int amount, boolean birthdayMonth) {
        // TODO: テストが赤くなっている原因(バグ)を探して直す
        int p = (int) Math.round(amount / 100.0);
        if (birthdayMonth) {
            p = p * 2;
        }
        return p;
    }

    public static void main(String[] args) {
        System.out.println(points(199, false));  // 1 のはず
        System.out.println(points(250, true));   // 4 のはず
        System.out.println(points(-500, false)); // 0 のはず
    }
}
