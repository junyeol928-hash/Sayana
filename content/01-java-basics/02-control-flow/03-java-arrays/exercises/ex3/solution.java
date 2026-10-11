public class WorkLog {

    /** 古い順の配列を受け取り、新しい順(逆順)にした新しい配列を返す。元の配列は変更しない。 */
    public static int[] reversed(int[] history) {
        if (history == null) {
            return new int[0];
        }
        int[] result = new int[history.length];
        for (int i = 0; i < history.length; i++) {
            result[i] = history[history.length - 1 - i];
        }
        return result;
    }

    /** 社員ごと(行ごと)の合計を返す。行の長さはバラバラでもよい。 */
    public static int[] rowTotals(int[][] minutes) {
        if (minutes == null) {
            return new int[0];
        }
        int[] totals = new int[minutes.length];
        for (int i = 0; i < minutes.length; i++) {
            int sum = 0;
            for (int m : minutes[i]) {
                sum += m;
            }
            totals[i] = sum;
        }
        return totals;
    }
}
