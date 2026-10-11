public class Overtime {

    /** 所定労働時間(1日8時間 = 480分) */
    static final int STANDARD_MINUTES = 480;

    /** 1日ごとの勤務時間(分)の配列から、その月の残業時間(分)の合計を返す */
    public static int totalOvertimeMinutes(int[] workedMinutes) {
        int total = 0;
        for (int minutes : workedMinutes) {
            if (minutes > STANDARD_MINUTES) {
                total += minutes - STANDARD_MINUTES;
            }
        }
        return total;
    }

    public static void main(String[] args) {
        int[] month = {480, 540, 600, 300};
        System.out.println(totalOvertimeMinutes(month)); // 180
    }
}
