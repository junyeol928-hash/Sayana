public class OvertimeCalc {

    static final int REGULAR_MINUTES = 480;   // 所定労働時間(1日8時間)
    static final int MONTHLY_LIMIT = 2700;    // 月の残業上限(45時間)

    // 1日分の残業(分)。480 分以下なら 0
    public static int overtimeOfDay(int workedMinutes) {
        // TODO: REGULAR_MINUTES を超えた分を返す(超えていなければ 0)
        return -1; // ← ダミー。書き換えてください
    }

    // 全日の残業の合計(分)
    public static int totalOvertime(int... dailyMinutes) {
        // TODO: dailyMinutes が null なら 0
        // TODO: 各日について overtimeOfDay を呼んで合計する
        return -1; // ← ダミー。書き換えてください
    }

    // 分を "時間:分"(分は2桁ゼロ埋め)にする
    public static String toHourMinute(int minutes) {
        // TODO: 時間 = minutes / 60、分 = minutes % 60
        // TODO: 分が 10 未満なら "0" を前に付けて連結する
        return ""; // ← ダミー。書き換えてください
    }

    // 残業合計が MONTHLY_LIMIT を「超えたら」true
    public static boolean exceedsLimit(int... dailyMinutes) {
        // TODO: totalOvertime を呼び出して上限と比べる
        return false; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        System.out.println(overtimeOfDay(540));                 // 60
        System.out.println(totalOvertime(540, 480, 600));       // 180
        System.out.println(totalOvertime());                    // 0
        System.out.println(toHourMinute(185));                  // 3:05
        System.out.println(exceedsLimit(540, 480, 600));        // false
    }
}
