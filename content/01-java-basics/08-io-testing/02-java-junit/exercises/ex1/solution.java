public class PointCalculator {

    /** 購入金額からポイントを計算する(100円で1ポイント、誕生月は2倍) */
    public static int points(int amount, boolean birthdayMonth) {
        if (amount <= 0) {
            return 0;                 // 返品・キャンセルはポイントなし
        }
        int p = amount / 100;         // int 同士の割り算は切り捨て
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
