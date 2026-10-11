import java.util.List;

public class GenericTools {

    public static <T extends Comparable<T>> T max(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        T best = list.get(0);
        for (T x : list) {
            if (x.compareTo(best) > 0) {
                best = x;
            }
        }
        return best;
    }

    public static void main(String[] args) {
        Pair<String, Integer> p = new Pair<>("りんご", 120);
        System.out.println(p.getKey() + " / " + p.getValue());          // りんご / 120
        Pair<Integer, String> s = p.swap();
        System.out.println(s.getKey() + " / " + s.getValue());          // 120 / りんご
        System.out.println(max(List.of(3, 9, 4)));                      // 9
        System.out.println(max(List.of("kiwi", "apple", "melon")));     // melon
    }
}

class Pair<K, V> {
    private final K key;
    private final V value;

    Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    K getKey() {
        return key;
    }

    V getValue() {
        return value;
    }

    Pair<V, K> swap() {
        return new Pair<>(value, key);
    }
}
