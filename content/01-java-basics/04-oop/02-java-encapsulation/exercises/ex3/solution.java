public class ScoreBook {
    private final int[] scores;

    ScoreBook(int[] scores) {
        if (scores == null) {
            throw new IllegalArgumentException("点数の配列が null です");
        }
        for (int s : scores) {
            checkScore(s);
        }
        this.scores = scores.clone();
    }

    int[] getScores() {
        return scores.clone();
    }

    void setScore(int index, int score) {
        if (index < 0 || index >= scores.length) {
            throw new IllegalArgumentException(
                    "index が範囲外です: " + index);
        }
        checkScore(score);
        scores[index] = score;
    }

    double average() {
        if (scores.length == 0) {
            return 0.0;
        }
        int sum = 0;
        for (int s : scores) {
            sum += s;
        }
        return (double) sum / scores.length;
    }

    private static void checkScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(
                    "点数は0〜100: " + score);
        }
    }

    public static void main(String[] args) {
        int[] src = {80, 90, 70};
        ScoreBook book = new ScoreBook(src);
        src[0] = 0;
        book.getScores()[1] = 0;
        System.out.println(book.average()); // 80.0
        book.setScore(2, 100);
        System.out.println(book.average()); // 90.0
    }
}
