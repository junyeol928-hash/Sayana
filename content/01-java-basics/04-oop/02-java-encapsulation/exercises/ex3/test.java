import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreBookTest {

    @Test
    @DisplayName("平均点を計算できる(0点・100点の境界値も有効)")
    void average() {
        assertEquals(80.0, new ScoreBook(new int[] {80, 90, 70}).average(), 1e-9,
                "80, 90, 70 の平均は 80.0 のはずです");
        assertEquals(50.0, new ScoreBook(new int[] {0, 100}).average(), 1e-9,
                "0点と100点は有効な点数です。平均は 50.0 のはずです");
        assertEquals(82.5, new ScoreBook(new int[] {80, 85}).average(), 1e-9,
                "80, 85 の平均は 82.5 です。int のまま割って小数が消えていないか確認してください");
        assertEquals(0.0, new ScoreBook(new int[0]).average(), 1e-9,
                "点数が0件なら 0.0 を返してください(0 で割らないように)");
    }

    @Test
    @DisplayName("コンストラクタに渡した元の配列を書き換えても、成績表は変わらない")
    void copyOnConstruct() {
        int[] src = {80, 90, 70};
        ScoreBook book = new ScoreBook(src);
        src[0] = 0;
        assertArrayEquals(new int[] {80, 90, 70}, book.getScores(),
                "元の配列の変更が成績表に漏れています。コンストラクタで配列をコピーして持ってください");
    }

    @Test
    @DisplayName("getScores の戻り値を書き換えても、成績表は変わらない")
    void copyOnGet() {
        ScoreBook book = new ScoreBook(new int[] {80, 90, 70});
        int[] got = book.getScores();
        got[1] = 0;
        assertArrayEquals(new int[] {80, 90, 70}, book.getScores(),
                "getScores の戻り値経由で内部が書き換えられています。コピーを返してください");
        assertEquals(80.0, book.average(), 1e-9, "外から書き換えられても平均は 80.0 のままのはずです");
    }

    @Test
    @DisplayName("範囲外の点数や null はコンストラクタで IllegalArgumentException")
    void invalidConstruct() {
        assertThrows(IllegalArgumentException.class, () -> new ScoreBook(new int[] {80, 101}),
                "101点は範囲外なので IllegalArgumentException を投げてください");
        assertThrows(IllegalArgumentException.class, () -> new ScoreBook(new int[] {-1, 50}),
                "-1点は範囲外なので IllegalArgumentException を投げてください");
        assertThrows(IllegalArgumentException.class, () -> new ScoreBook(null),
                "null が渡されたら IllegalArgumentException を投げてください(NullPointerException ではなく)");
    }

    @Test
    @DisplayName("setScore で点数を訂正できる。index や点数が範囲外なら例外で、中身は変わらない")
    void setScore() {
        ScoreBook book = new ScoreBook(new int[] {80, 90, 70});
        book.setScore(2, 100);
        assertArrayEquals(new int[] {80, 90, 100}, book.getScores(), "setScore(2, 100) で3番目が100点になるはずです");
        assertThrows(IllegalArgumentException.class, () -> book.setScore(3, 50),
                "index 3 は範囲外(0〜2)なので IllegalArgumentException を投げてください");
        assertThrows(IllegalArgumentException.class, () -> book.setScore(-1, 50),
                "index -1 は範囲外なので IllegalArgumentException を投げてください");
        assertThrows(IllegalArgumentException.class, () -> book.setScore(0, 101),
                "101点は範囲外なので IllegalArgumentException を投げてください");
        assertArrayEquals(new int[] {80, 90, 100}, book.getScores(),
                "不正な訂正を拒否したときは中身を変えないでください(チェックしてから代入)");
    }
}
