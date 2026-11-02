public class ScoreBook {

    static void addBonusInPlace(int[] scores, int bonus) {
        // TODO: scores の各要素に bonus を足す
        // 下の書き方だと、引数 scores が新しい配列を指すだけで、呼び出し元の配列は変わらない
        scores = new int[scores.length];
    }

    static int[] withBonus(int[] scores, int bonus) {
        // TODO: 新しい配列を作り、各要素に bonus を足した値を入れて返す
        int[] result = scores;   // ← コピーではなく「同じ配列を指す矢印」が増えただけ
        for (int i = 0; i < result.length; i++) {
            result[i] += bonus;
        }
        return result;
    }

    static boolean sameScores(int[] a, int[] b) {
        // TODO: 長さと全要素を比べる
        return a == b;           // ← 同じ配列かどうかしか見ていない
    }

    public static void main(String[] args) {
        int[] t = {60, 75, 90};
        int[] u = withBonus(t, 5);
        System.out.println(java.util.Arrays.toString(t)); // [60, 75, 90] になるはず
        System.out.println(java.util.Arrays.toString(u)); // [65, 80, 95]
        System.out.println(sameScores(new int[]{1, 2}, new int[]{1, 2})); // true
    }
}
