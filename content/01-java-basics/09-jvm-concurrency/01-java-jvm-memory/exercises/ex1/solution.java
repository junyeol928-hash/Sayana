public class ScoreBook {

    static void addBonusInPlace(int[] scores, int bonus) {
        for (int i = 0; i < scores.length; i++) {
            scores[i] += bonus;   // 呼び出し元と同じ配列の中身を書き換える
        }
    }

    static int[] withBonus(int[] scores, int bonus) {
        int[] result = new int[scores.length];   // ヒープに別の配列を作る
        for (int i = 0; i < scores.length; i++) {
            result[i] = scores[i] + bonus;
        }
        return result;
    }

    static boolean sameScores(int[] a, int[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        int[] t = {60, 75, 90};
        int[] u = withBonus(t, 5);
        System.out.println(java.util.Arrays.toString(t)); // [60, 75, 90]
        System.out.println(java.util.Arrays.toString(u)); // [65, 80, 95]
        System.out.println(sameScores(new int[]{1, 2}, new int[]{1, 2})); // true
    }
}
