import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketQueueTest {

    @Test
    @DisplayName("compareTo:優先度が高い(数字が小さい)方が前なら負の値")
    void compareByPriority() {
        Ticket high = new Ticket("T-9", 1, 900);
        Ticket low = new Ticket("T-1", 3, 0);
        assertTrue(high.compareTo(low) < 0,
                "priority 1 は priority 3 より前なので、compareTo は負の値を返してください");
        assertTrue(low.compareTo(high) > 0,
                "priority 3 は priority 1 より後なので、compareTo は正の値を返してください");
    }

    @Test
    @DisplayName("compareTo:すべて同じなら 0")
    void compareEqual() {
        assertEquals(0, new Ticket("T-1", 2, 600).compareTo(new Ticket("T-1", 2, 600)),
                "id・priority・receivedAt がすべて同じなら 0 を返してください");
    }

    @Test
    @DisplayName("優先度順 → 同じ優先度なら受付が早い順に並ぶ")
    void orderByPriorityThenTime() {
        Ticket[] ts = {
            new Ticket("T-001", 2, 600),
            new Ticket("T-002", 1, 630),
            new Ticket("T-003", 2, 540),
        };
        assertArrayEquals(new String[] {"T-002", "T-003", "T-001"}, TicketQueue.order(ts),
                "優先度 1 が先頭、優先度 2 同士は receivedAt が小さい方が先です");
    }

    @Test
    @DisplayName("優先度も受付時刻も同じなら id の辞書順")
    void orderById() {
        Ticket[] ts = {
            new Ticket("T-020", 3, 720),
            new Ticket("T-010", 3, 720),
            new Ticket("T-015", 3, 720),
        };
        assertArrayEquals(new String[] {"T-010", "T-015", "T-020"}, TicketQueue.order(ts),
                "priority と receivedAt が同じときは id.compareTo(other.id) で辞書順に並べてください");
    }

    @Test
    @DisplayName("引数の配列そのものは並べ替えない")
    void originalUntouched() {
        Ticket a = new Ticket("T-001", 3, 100);
        Ticket b = new Ticket("T-002", 1, 200);
        Ticket[] ts = {a, b};
        TicketQueue.order(ts);
        assertTrue(ts[0] == a && ts[1] == b,
                "引数の配列の並びが変わっています。Arrays.copyOf でコピーしてから並べ替えてください");
    }

    @Test
    @DisplayName("空の配列なら空の配列を返す")
    void empty() {
        assertArrayEquals(new String[0], TicketQueue.order(new Ticket[0]),
                "チケットが0件なら長さ0の配列を返してください");
    }
}
