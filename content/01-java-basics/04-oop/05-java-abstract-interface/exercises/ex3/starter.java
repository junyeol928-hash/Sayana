import java.util.Arrays;

class Ticket implements Comparable<Ticket> {
    final String id;
    final int priority;     // 1 が最優先
    final int receivedAt;   // 受付時刻(0時からの経過分)

    Ticket(String id, int priority, int receivedAt) {
        this.id = id;
        this.priority = priority;
        this.receivedAt = receivedAt;
    }

    @Override
    public int compareTo(Ticket other) {
        // TODO: priority が小さい方が前
        // TODO: priority が同じなら receivedAt が小さい方が前
        // TODO: それも同じなら id の辞書順(id.compareTo(other.id))
        return 0;
    }
}

public class TicketQueue {

    static String[] order(Ticket[] tickets) {
        // TODO: 引数の配列をコピーしてから Arrays.sort で並べ替える
        // TODO: 並んだ順にチケット番号(id)を入れた配列を返す
        return new String[0];
    }

    public static void main(String[] args) {
        Ticket[] ts = {
            new Ticket("T-001", 2, 600),
            new Ticket("T-002", 1, 630),
            new Ticket("T-003", 2, 540),
        };
        System.out.println(Arrays.toString(order(ts)));  // [T-002, T-003, T-001]
    }
}
