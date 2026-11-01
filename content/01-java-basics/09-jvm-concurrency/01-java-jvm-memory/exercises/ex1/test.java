import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreBookTest {

    @Test
    @DisplayName("addBonusInPlace は呼び出し元の配列そのものを書き換える")
    void addBonusInPlace() {
        int[] s = {60, 75, 90};
        ScoreBook.addBonusInPlace(s, 5);
        assertArrayEquals(new int[]{65, 80, 95}, s,
                "呼び出し元の配列が変わっていません。引数に new int[...] を代入するのではなく、scores[i] += bonus のように要素を書き換えてください");
    }

    @Test
    @DisplayName("withBonus は bonus を足した新しい配列を返す")
    void withBonusReturnsNewValues() {
        int[] t = {60, 75, 90};
        assertArrayEquals(new int[]{65, 80, 95}, ScoreBook.withBonus(t, 5),
                "戻り値の配列の中身が違います。各要素に bonus を足した値を入れてください");
    }

    @Test
    @DisplayName("withBonus は元の配列を変えず、別の配列を返す")
    void withBonusKeepsOriginal() {
        int[] t = {60, 75, 90};
        int[] u = ScoreBook.withBonus(t, 5);
        assertArrayEquals(new int[]{60, 75, 90}, t,
                "元の配列が書き換わっています。int[] result = scores; はコピーではありません。new int[scores.length] で新しい配列を作ってください");
        assertNotSame(t, u, "引数と同じ配列を返しています。新しい配列を作って返してください");
    }

    @Test
    @DisplayName("withBonus は長さ0の配列でも動く(境界値)")
    void withBonusEmpty() {
        assertArrayEquals(new int[0], ScoreBook.withBonus(new int[0], 5),
                "空の配列を渡したら空の配列を返してください");
    }

    @Test
    @DisplayName("sameScores は別の配列でも中身が同じなら true、違えば false")
    void sameScores() {
        assertTrue(ScoreBook.sameScores(new int[]{1, 2, 3}, new int[]{1, 2, 3}),
                "別々に作った配列でも中身が同じなら true にしてください(== は同じ配列かどうかしか見ません)");
        assertFalse(ScoreBook.sameScores(new int[]{1, 2, 3}, new int[]{1, 2, 4}),
                "要素が1つでも違えば false にしてください");
        assertFalse(ScoreBook.sameScores(new int[]{1, 2}, new int[]{1, 2, 3}),
                "長さが違えば false にしてください(先に length を比べると安全です)");
    }
}
