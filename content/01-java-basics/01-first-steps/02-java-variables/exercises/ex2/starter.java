public class TimeFormatter {

    /**
     * 秒数を "時:分:秒" の文字列にする。
     * @param totalSeconds 勤務時間(秒)
     */
    public static String format(int totalSeconds) {
        // TODO: 時・分・秒を計算して、下の3つの変数に入れる
        //   ヒント: int 同士の / は切り捨て(7500 / 3600 は 2)
        //           % は余り(7500 % 3600 は 300)
        int hours = 0;
        int minutes = 0;
        int seconds = 0;

        // ここは書き換えなくてOK(%02d は「2桁になるよう左を0で埋める」という指定)
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }
}
