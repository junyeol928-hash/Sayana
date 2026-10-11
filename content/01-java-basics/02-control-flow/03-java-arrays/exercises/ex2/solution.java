public class ScoreStats {

    /** 60点以上の人数を返す。null または空配列なら 0。 */
    public static int countPassed(int[] scores) {
        if (scores == null || scores.length == 0) {
            return 0;
        }
        int count = 0;
        for (int s : scores) {
            if (s >= 60) {
                count++;
            }
        }
        return count;
    }

    /** 平均点以上(平均ちょうどを含む)の人数を返す。null または空配列なら 0。 */
    public static int countAtOrAboveAverage(int[] scores) {
        if (scores == null || scores.length == 0) {
            return 0;
        }
        int sum = 0;
        for (int s : scores) {
            sum += s;
        }
        double average = (double) sum / scores.length;

        int count = 0;
        for (int s : scores) {
            if (s >= average) {
                count++;
            }
        }
        return count;
    }
}
