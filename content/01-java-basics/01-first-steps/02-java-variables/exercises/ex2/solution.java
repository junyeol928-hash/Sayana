public class TimeFormatter {

    /**
     * 秒数を "時:分:秒" の文字列にする。
     * @param totalSeconds 勤務時間(秒)
     */
    public static String format(int totalSeconds) {
        final int secondsPerHour = 3600;
        final int secondsPerMinute = 60;

        int hours = totalSeconds / secondsPerHour;   // 時間(int 同士の割り算なので切り捨て)
        int rest = totalSeconds % secondsPerHour;    // 時間の分を除いた残りの秒数
        int minutes = rest / secondsPerMinute;       // 分
        int seconds = rest % secondsPerMinute;       // 秒

        // ここは書き換えなくてOK(%02d は「2桁になるよう左を0で埋める」という指定)
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }
}
