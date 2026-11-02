import java.util.ArrayList;
import java.util.List;

public class RecentLog {
    private final int capacity;
    private final List<String> messages = new ArrayList<>();

    RecentLog(int capacity) {
        // TODO: capacity が 0 以下なら IllegalArgumentException を投げる
        this.capacity = capacity;
    }

    void add(String message) {
        messages.add(message);
        // TODO: capacity を超えたら古いもの(先頭)から消す。今のままだと無限に溜まる
    }

    List<String> recent() {
        // TODO: コピーを返す。今のままだと中のリストへの参照がそのまま外に出る
        return messages;
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
