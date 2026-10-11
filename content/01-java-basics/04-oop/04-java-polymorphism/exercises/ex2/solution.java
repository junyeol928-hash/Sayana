public class Checkout {

    static int total(Payment p, int amount) {
        return amount + p.fee(amount);
    }

    static String receipt(Payment p, int amount) {
        return p.label() + "払い: " + amount + "円 + 手数料" + p.fee(amount)
                + "円 = " + total(p, amount) + "円";
    }

    public static void main(String[] args) {
        Payment[] payments = { new CashPayment(), new CardPayment(), new PointPayment() };
        for (Payment p : payments) {
            System.out.println(receipt(p, 800));
        }
        // 現金払い: 800円 + 手数料0円 = 800円
        // カード払い: 800円 + 手数料100円 = 900円
        // ポイント払い: 800円 + 手数料50円 = 850円
    }
}

class Payment {
    String label() {
        return "不明";
    }

    int fee(int amount) {
        return 0;
    }
}

class CashPayment extends Payment {
    @Override
    String label() {
        return "現金";
    }
}

class CardPayment extends Payment {
    static final int MIN_FEE = 100;

    @Override
    String label() {
        return "カード";
    }

    @Override
    int fee(int amount) {
        int fee = amount * 3 / 100;
        return Math.max(fee, MIN_FEE);
    }
}

class PointPayment extends Payment {
    @Override
    String label() {
        return "ポイント";
    }

    @Override
    int fee(int amount) {
        return amount < 1000 ? 50 : 0;
    }
}
