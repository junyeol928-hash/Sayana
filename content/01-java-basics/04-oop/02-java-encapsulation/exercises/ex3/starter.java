public class ScoreBook {
    private final int[] scores;

    ScoreBook(int[] scores) {
        // TODO: scores が null なら IllegalArgumentException
        // TODO: 0〜100 の範囲外の点数があれば IllegalArgumentException
        // TODO: 受け取った配列をそのまま持たず、コピーして持つ
        this.scores = scores; // ← このままだと外から書き換えられてしまう
    }

    int[] getScores() {
        // TODO: 内部の配列そのものではなく、コピーを返す
        return scores;
    }

    void setScore(int index, int score) {
        // TODO: index が範囲外、または score が 0〜100 の範囲外なら IllegalArgumentException
        scores[index] = score;
    }

    double average() {
        // TODO: 平均点を返す(0件なら 0.0)
        return -1; // ← ダミー。書き換えてください
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
