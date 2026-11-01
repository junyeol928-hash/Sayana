import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecentLogTest {

    @Test
    @DisplayName("capacity 以下なら追加した順にすべて覚えている")
    void keepsAllWithinCapacity() {
        RecentLog log = new RecentLog(3);
        log.add("a");
        log.add("b");
        assertEquals(List.of("a", "b"), log.recent(), "追加した順(古い順)に並んでいるはずです");
        assertEquals(2, log.size(), "2件追加したら size は 2 のはずです");
    }

    @Test
    @DisplayName("capacity を超えたら古いものから消える")
    void dropsOldest() {
        RecentLog log = new RecentLog(3);
        for (String m : new String[]{"a", "b", "c", "d", "e"}) {
            log.add(m);
        }
        assertEquals(3, log.size(), "capacity 3 なら 5件追加しても 3件に収まるはずです。add の中で先頭を remove(0) していますか?");
        assertEquals(List.of("c", "d", "e"), log.recent(), "残るのは新しい3件(c, d, e)です。消すのは先頭(いちばん古いもの)からです");
    }

    @Test
    @DisplayName("大量に追加しても capacity 件しか溜まらない")
    void doesNotGrowForever() {
        RecentLog log = new RecentLog(100);
        for (int i = 1; i <= 10_000; i++) {
            log.add("msg-" + i);
        }
        assertEquals(100, log.size(), "1万件追加しても 100件に収まるはずです。上限なしに溜め込むとメモリリークになります");
        List<String> r = log.recent();
        assertEquals("msg-9901", r.get(0), "残っている最も古いログは msg-9901 のはずです");
        assertEquals("msg-10000", r.get(r.size() - 1), "最も新しいログは msg-10000 のはずです");
    }

    @Test
    @DisplayName("recent が返すのはコピー。書き換えても中身は変わらない")
    void recentReturnsCopy() {
        RecentLog log = new RecentLog(3);
        log.add("a");
        log.add("b");
        List<String> view = log.recent();
        view.clear();
        assertEquals(2, log.size(), "返したリストを clear したら中身まで消えました。return messages; ではなく new ArrayList<>(messages) を返してください");
        assertNotSame(log.recent(), log.recent(), "recent() は呼ぶたびに新しいリストを返してください");
    }

    @Test
    @DisplayName("capacity が 0 以下なら IllegalArgumentException")
    void rejectsInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new RecentLog(0),
                "capacity 0 では何も覚えられません。コンストラクタで IllegalArgumentException を投げてください");
        assertThrows(IllegalArgumentException.class, () -> new RecentLog(-1),
                "capacity がマイナスのときも IllegalArgumentException を投げてください");
    }
}
