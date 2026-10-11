import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TypedCacheTest {

    @Test
    @DisplayName("put した値を get で取り出せる(キャスト不要)")
    void putAndGet() {
        TypedCache<String, Integer> c = new TypedCache<>(3);
        c.put("りんご", 120);
        c.put("みかん", 80);
        Integer apple = c.get("りんご");
        assertEquals(120, apple, "put した値が get で取り出せません。Map に保存していますか?");
        assertEquals(80, c.get("みかん"), "みかんの値は 80 です");
        assertNull(c.get("ぶどう"), "存在しないキーなら null を返してください");
        assertEquals(2, c.size(), "2件 put したので size() は 2 です");
    }

    @Test
    @DisplayName("容量を超えたら、いちばん古く追加されたキーを削除する")
    void evictOldest() {
        TypedCache<String, Integer> c = new TypedCache<>(2);
        c.put("A", 1);
        c.put("B", 2);
        c.put("C", 3);
        assertEquals(2, c.size(), "容量 2 のキャッシュに3件入れたら、件数は 2 のままのはずです");
        assertNull(c.get("A"), "いちばん古い A が削除されていません。追加順を覚える LinkedHashMap を使っていますか?");
        assertEquals(2, c.get("B"), "B は削除されないはずです");
        assertEquals(3, c.get("C"), "C は今入れたばかりなので残っているはずです");
    }

    @Test
    @DisplayName("既存のキーへの put は上書きで、件数も並び順も変わらない")
    void overwriteKeepsOrder() {
        TypedCache<String, Integer> c = new TypedCache<>(2);
        c.put("A", 1);
        c.put("B", 2);
        c.put("A", 10);
        assertEquals(2, c.size(), "既存のキーへの put で件数が変わってはいけません");
        assertEquals(10, c.get("A"), "既存のキーへの put は値の上書きです");
        c.put("C", 3);
        assertNull(c.get("A"), "A は上書きしても「最初に追加された」キーのままなので、C を入れたときに削除されるはずです");
        assertEquals(2, c.get("B"), "B は残っているはずです");
    }

    @Test
    @DisplayName("get のヒット回数とミス回数を数える")
    void hitAndMiss() {
        TypedCache<Integer, String> c = new TypedCache<>(5);
        c.put(1, "一");
        c.get(1);
        c.get(1);
        c.get(2);
        assertEquals(2, c.hits(), "見つかった get は2回です。ヒットのときに hits を +1 していますか?");
        assertEquals(1, c.misses(), "見つからなかった get は1回です。ミスのときに misses を +1 していますか?");
    }

    @Test
    @DisplayName("容量 1 でも動き、容量 0 以下なら IllegalArgumentException")
    void capacityBoundary() {
        TypedCache<String, String> one = new TypedCache<>(1);
        one.put("X", "x");
        one.put("Y", "y");
        assertEquals(1, one.size(), "容量 1 なら常に1件だけ保持します");
        assertEquals("y", one.get("Y"), "容量 1 のときは最後に入れたものが残ります");
        assertThrows(IllegalArgumentException.class, () -> new TypedCache<String, String>(0),
                "容量 0 では IllegalArgumentException を投げてください");
        assertThrows(IllegalArgumentException.class, () -> new TypedCache<String, String>(-1),
                "容量がマイナスでも IllegalArgumentException を投げてください");
    }
}
