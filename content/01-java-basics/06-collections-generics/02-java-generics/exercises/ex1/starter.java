import java.util.List;

public class GenericTools {

    public static <T extends Comparable<T>> T max(List<T> list) {
        // TODO: list が null または空なら null を返す

        // TODO: 先頭の要素を仮の最大値にして、残りと compareTo で比べる

        return null; // ← ダミー。書き換えてください
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
        // TODO: key を返す
        return null;
    }

    V getValue() {
        // TODO: value を返す
        return null;
    }

    Pair<V, K> swap() {
        // TODO: キーと値を入れ替えた新しい Pair を返す
        return null;
    }
}
