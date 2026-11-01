public class TicketMachine {
    private final String counterName;
    private int issued = 0;

    TicketMachine(String counterName) {
        this.counterName = counterName;
    }

    Ticket issue() {
        issued++;
        return new Ticket(issued);   // この発行機に結び付いた Ticket ができる
    }

    int issuedCount() {
        return issued;
    }

    class Ticket {
        private final int number;

        Ticket(int number) {
            this.number = number;
        }

        int number() {
            return number;
        }

        String label() {
            // 外側の counterName をそのまま読める
            return counterName + "-" + String.format("%03d", number);
        }

        int behind() {
            // 外側の issued は発行のたびに増えるので、呼んだ時点の値で計算する
            return TicketMachine.this.issued - number;
        }
    }

    public static void main(String[] args) {
        TicketMachine a = new TicketMachine("A窓口");
        Ticket t1 = a.issue();
        a.issue();
        Ticket t3 = a.issue();
        System.out.println(t1.label() + " 後ろに " + t1.behind() + " 人");
        System.out.println(t3.label() + " 後ろに " + t3.behind() + " 人");
    }
}
