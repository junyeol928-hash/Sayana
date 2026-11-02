import java.util.ArrayList;
import java.util.List;

public class RecentLog {
    private final int capacity;
    private final List<String> messages = new ArrayList<>();

    RecentLog(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity は 1 以上: " + capacity);
        }
        this.capacity = capacity;
    }

    void add(String message) {
        messages.add(message);
        while (messages.size() > capacity) {
            messages.remove(0);   // 古いものへの参照を外す → GC の対象になれる
        }
    }

    List<String> recent() {
        return new ArrayList<>(messages);   // 中身をコピーした別のリスト
    }

    int size() {
        return messages.size();
    }

    public static void main(String[] args) {
        RecentLog log = new RecentLog(3);
        log.add("a");
        log.add("b");
        log.add("c");
        log.add("d");
        System.out.println(log.recent()); // [b, c, d]
        log.recent().clear();
        System.out.println(log.size());   // 3
    }
}
