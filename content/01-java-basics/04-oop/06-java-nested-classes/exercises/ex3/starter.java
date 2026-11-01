public class TicketMachine {
    private final String counterName;
    private int issued = 0;

    TicketMachine(String counterName) {
        this.counterName = counterName;
    }

    Ticket issue() {
        // TODO: 発行枚数を1増やし、その番号の Ticket を作って返す
        return new Ticket(0);
    }

    int issuedCount() {
        // TODO: これまでに発行した枚数を返す
        return 0;
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
            // TODO: "A窓口-001" の形式(番号は3桁のゼロ埋め)で返す
            return "";
        }

        int behind() {
            // TODO: 自分より後に発行された枚数を返す(外側の最新の発行枚数を使う)
            return 0;
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
