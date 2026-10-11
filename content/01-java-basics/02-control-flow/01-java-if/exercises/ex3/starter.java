public class HourlyRate {

    public static int rate(int dayOfWeek, boolean publicHoliday, boolean night) {
        // TODO: dayOfWeek が 1〜7 以外なら -1 を返す(ガード節)

        int base = 0;
        // TODO: 祝日なら 1500。そうでなければ switch 式で
        //       平日(1〜5) 1200 / 土曜(6) 1300 / 日曜(7) 1500 を base に入れる
        //       例: base = switch (dayOfWeek) { case 1, 2, 3, 4, 5 -> 1200; ... };

        // TODO: 夜勤なら base に 250 を足す

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
