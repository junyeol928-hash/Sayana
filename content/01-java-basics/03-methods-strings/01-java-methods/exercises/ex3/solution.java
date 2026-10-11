public class OvertimeCalc {

    static final int REGULAR_MINUTES = 480;   // 所定労働時間(1日8時間)
    static final int MONTHLY_LIMIT = 2700;    // 月の残業上限(45時間)

    public static int overtimeOfDay(int workedMinutes) {
        if (workedMinutes <= REGULAR_MINUTES) {
            return 0;
        }
        return workedMinutes - REGULAR_MINUTES;
    }

    public static int totalOvertime(int... dailyMinutes) {
        if (dailyMinutes == null) {
            return 0;
        }
        int total = 0;
        for (int m : dailyMinutes) {
            total += overtimeOfDay(m);
        }
        return total;
    }

    public static String toHourMinute(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        String mm = (m < 10) ? "0" + m : "" + m;
        return h + ":" + mm;
    }

    public static boolean exceedsLimit(int... dailyMinutes) {
        return totalOvertime(dailyMinutes) > MONTHLY_LIMIT;
    }

    public static void main(String[] args) {
        System.out.println(overtimeOfDay(540));                 // 60
        System.out.println(totalOvertime(540, 480, 600));       // 180
        System.out.println(totalOvertime());                    // 0
        System.out.println(toHourMinute(185));                  // 3:05
        System.out.println(exceedsLimit(540, 480, 600));        // false
    }
}
