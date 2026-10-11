public class Checkout {

    static int total(Payment p, int amount) {
        // TODO: 金額 + 手数料 を返す(p.fee(amount) を使う。種類の判定はしない)
        return 0;
    }

    static String receipt(Payment p, int amount) {
        // TODO: "カード払い: 5000円 + 手数料150円 = 5150円" の形式で返す
        return "";
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
    // TODO: label() をオーバーライドして "現金" を返す
    // (fee は親と同じ 0 なので、オーバーライドしなくてもよい)
}

class CardPayment extends Payment {
    static final int MIN_FEE = 100;

    // TODO: label() をオーバーライドして "カード" を返す

    // TODO: fee(int amount) をオーバーライドして、金額の 3%(切り捨て)を返す。ただし最低 MIN_FEE 円
}

class PointPayment extends Payment {
    // TODO: label() をオーバーライドして "ポイント" を返す

    // TODO: fee(int amount) をオーバーライドして、1000 円未満なら 50、以上なら 0 を返す
}
