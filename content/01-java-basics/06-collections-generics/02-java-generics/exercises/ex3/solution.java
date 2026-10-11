import java.util.LinkedHashMap;
import java.util.Map;

public class TypedCache<K, V> {

    private final int capacity;
    private final Map<K, V> map = new LinkedHashMap<>();
    private int hits;
    private int misses;

    public TypedCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity は 1 以上にしてください: " + capacity);
        }
        this.capacity = capacity;
    }

    public void put(K key, V value) {
        map.put(key, value);
        if (map.size() > capacity) {
            K oldest = map.keySet().iterator().next();
            map.remove(oldest);
        }
    }

    public V get(K key) {
        if (map.containsKey(key)) {
            hits++;
            return map.get(key);
        }
        misses++;
        return null;
    }

    public int size() {
        return map.size();
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
