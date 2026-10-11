public class HourlyRate {

    public static int rate(int dayOfWeek, boolean publicHoliday, boolean night) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            return -1;
        }

        int base;
        if (publicHoliday) {
            base = 1500;
        } else {
            base = switch (dayOfWeek) {
                case 1, 2, 3, 4, 5 -> 1200;
                case 6 -> 1300;
                default -> 1500; // ここに来るのは 7(日曜)だけ
            };
        }

        if (night) {
            base += 250;
        }
        return base;
    }

    public static void main(String[] args) {
        System.out.println(rate(1, false, false)); // 1200
        System.out.println(rate(6, false, false)); // 1300
        System.out.println(rate(3, true, false));  // 1500
        System.out.println(rate(7, false, true));  // 1750
        System.out.println(rate(8, true, true));   // -1
    }
}
