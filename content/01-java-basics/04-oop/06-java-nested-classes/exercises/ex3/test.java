import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TicketMachineTest {

    @Test
    @DisplayName("Ticket は static のない内部クラスになっている")
    void ticketIsInnerClass() {
        assertFalse(Modifier.isStatic(TicketMachine.Ticket.class.getModifiers()),
                "Ticket に static を付けないでください。内部クラスにすると外側の窓口名や発行枚数を読めます");
    }

    @Test
    @DisplayName("発行するたびに番号が 1, 2, 3 と進む")
    void numbersIncrease() {
        TicketMachine m = new TicketMachine("A窓口");
        assertEquals(1, m.issue().number(), "最初の整理券の番号は 1 です。issue() の中で発行枚数を1増やしてから Ticket を作ってください");
        assertEquals(2, m.issue().number(), "2枚目の整理券の番号は 2 です");
        assertEquals(3, m.issue().number(), "3枚目の整理券の番号は 3 です");
        assertEquals(3, m.issuedCount(), "3枚発行したら issuedCount() は 3 です");
    }

    @Test
    @DisplayName("label() は窓口名と3桁ゼロ埋めの番号")
    void label() {
        TicketMachine m = new TicketMachine("A窓口");
        assertEquals("A窓口-001", m.issue().label(),
                "label() は \"窓口名-番号\" です。番号は String.format(\"%03d\", number) で3桁にそろえてください");
        for (int i = 0; i < 10; i++) {
            m.issue();
        }
        assertEquals("A窓口-012", m.issue().label(), "12枚目の整理券は \"A窓口-012\" です");
    }

    @Test
    @DisplayName("behind() は呼んだ時点の最新の発行枚数で計算する")
    void behindUsesLatestCount() {
        TicketMachine m = new TicketMachine("A窓口");
        TicketMachine.Ticket first = m.issue();
        assertEquals(0, first.behind(), "発行した直後は、後ろに誰もいないので 0 です");
        m.issue();
        m.issue();
        assertEquals(2, first.behind(),
                "1枚目のあとに2枚発行したので 2 です。外側の発行枚数(issued)から自分の番号を引いてください");
    }

    @Test
    @DisplayName("窓口ごとに番号は別々に進む")
    void machinesAreIndependent() {
        TicketMachine a = new TicketMachine("A窓口");
        TicketMachine b = new TicketMachine("B窓口");
        TicketMachine.Ticket a1 = a.issue();
        b.issue();
        b.issue();
        TicketMachine.Ticket b3 = b.issue();
        assertEquals("A窓口-001", a1.label(), "A窓口の整理券に A窓口の名前が入っていません");
        assertEquals("B窓口-003", b3.label(),
                "B窓口の3枚目は \"B窓口-003\" です。発行枚数を static にしていませんか?");
        assertEquals(0, a1.behind(), "B窓口で発行しても、A窓口の整理券の behind() は増えません");
        assertEquals(1, a.issuedCount(), "A窓口の発行枚数は 1 のままです");
    }
}
