import java.util.LinkedHashMap;
import java.util.Map;

public class TypedCache<K, V> {

    private final int capacity;
    // TODO: 追加順を覚えている Map を用意する
    private int hits;
    private int misses;

    public TypedCache(int capacity) {
        // TODO: capacity が 1 未満なら IllegalArgumentException を投げる
        this.capacity = capacity;
    }

    public void put(K key, V value) {
        // TODO: Map に保存する
        // TODO: 件数が capacity を超えたら、いちばん古いキーを1件削除する
    }

    public V get(K key) {
        // TODO: キーがあればヒットを数えて値を返す。なければミスを数えて null を返す
        return null;
    }

    public int size() {
        // TODO: 今の件数を返す
        return -1;
    }

    public int hits() {
        return hits;
    }

    public int misses() {
        return misses;
    }

    public static void main(String[] args) {
        TypedCache<String, Integer> c = new TypedCache<>(2);
        c.put("A", 1);
        c.put("B", 2);
        c.put("C", 3);
        System.out.println(c.get("A"));  // null
        System.out.println(c.get("C"));  // 3
        System.out.println("件数=" + c.size() + " ヒット=" + c.hits() + " ミス=" + c.misses());
        // 件数=2 ヒット=1 ミス=1
    }
}
