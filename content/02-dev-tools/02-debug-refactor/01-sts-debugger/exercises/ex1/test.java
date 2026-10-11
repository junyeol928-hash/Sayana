import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LateCounterTest {

    private static final int NINE = 540; // 9:00

    @Test
    @DisplayName("1日目の遅刻も数える")
    void firstDayIsCounted() {
        int[] arrivals = {550, 530, 535}; // 9:10, 8:50, 8:55
        assertEquals(1, LateCounter.countLate(arrivals, NINE),
                "1日目(配列の先頭)の 9:10 出社が数えられていません。"
                + "ループ変数 i が何から始まっているか、Variables ビューで確かめてください");
    }

    @Test
    @DisplayName("1日分しかなく、その日が遅刻なら 1")
    void singleDay() {
        assertEquals(1, LateCounter.countLate(new int[] {600}, NINE),
                "要素が1つ(10:00 出社)でも遅刻として数えてください。先頭の要素を飛ばしていませんか?");
    }

    @Test
    @DisplayName("始業時刻ちょうどは遅刻ではない、1分過ぎたら遅刻(境界値)")
    void boundary() {
        int[] arrivals = {540, 540, 541}; // 9:00, 9:00, 9:01
        assertEquals(1, LateCounter.countLate(arrivals, NINE),
                "9:00 ちょうどは遅刻ではなく、9:01 は遅刻です。"
                + "比較が >= になっていないか確認してください");
    }

    @Test
    @DisplayName("全員時間内なら 0")
    void allOnTime() {
        int[] arrivals = {500, 510, 539, 540};
        assertEquals(0, LateCounter.countLate(arrivals, NINE),
                "8:20〜9:00 の出社はすべて遅刻ではありません。始業時刻ちょうど(540)も遅刻に数えていませんか?");
    }

    @Test
    @DisplayName("出社記録が空なら 0")
    void empty() {
        assertEquals(0, LateCounter.countLate(new int[0], NINE),
                "出社記録が空の配列なら 0 を返してください");
    }
}
