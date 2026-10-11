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
        if (priority != other.priority) {
            return Integer.compare(priority, other.priority);
        }
        if (receivedAt != other.receivedAt) {
            return Integer.compare(receivedAt, other.receivedAt);
        }
        return id.compareTo(other.id);
    }
}

public class TicketQueue {

    static String[] order(Ticket[] tickets) {
        Ticket[] sorted = Arrays.copyOf(tickets, tickets.length);
        Arrays.sort(sorted);
        String[] ids = new String[sorted.length];
        for (int i = 0; i < sorted.length; i++) {
            ids[i] = sorted[i].id;
        }
        return ids;
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
