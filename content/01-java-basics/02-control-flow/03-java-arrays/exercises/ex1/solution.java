public class SalesReport {

    /** 売上の合計を返す。null または空配列なら 0。 */
    public static long total(int[] sales) {
        if (sales == null || sales.length == 0) {
            return 0;
        }
        long sum = 0;
        for (int s : sales) {
            sum += s;
        }
        return sum;
    }

    /** 1日あたりの平均売上を返す。null または空配列なら 0.0。 */
    public static double average(int[] sales) {
        if (sales == null || sales.length == 0) {
            return 0.0;
        }
        return (double) total(sales) / sales.length;
    }

    /** 売上が最大の日のインデックスを返す。同点なら早い日。null または空配列なら -1。 */
    public static int bestDay(int[] sales) {
        if (sales == null || sales.length == 0) {
            return -1;
        }
        int best = 0;
        for (int i = 1; i < sales.length; i++) {
            if (sales[i] > sales[best]) {
                best = i;
            }
        }
        return best;
    }
}
