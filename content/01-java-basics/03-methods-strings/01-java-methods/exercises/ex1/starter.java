public class InvoiceCalc {

    // 小計(単価 × 数量)を返す
    public static int subtotal(int unitPrice, int qty) {
        // TODO: 単価 × 数量 を返す
        return 0; // ← ダミー。書き換えてください
    }

    // 消費税 10%(1円未満は切り捨て)を返す
    public static int tax(int amount) {
        // TODO: amount の 10% を返す(int の割り算で切り捨てになる)
        return 0; // ← ダミー。書き換えてください
    }

    // 税込合計(値引きなし)
    public static int total(int unitPrice, int qty) {
        // TODO: subtotal と tax を呼び出して税込合計を返す
        //       (値引きあり版の total に 0 を渡して委ねてもよい)
        return 0; // ← ダミー。書き換えてください
    }

    // 値引き後の税込合計(オーバーロード)
    public static int total(int unitPrice, int qty, int discount) {
        // TODO: 小計 - 値引き を求める(マイナスなら 0 にする)
        // TODO: その金額に消費税を足して返す
        return 0; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        System.out.println(subtotal(980, 3));     // 2940
        System.out.println(tax(999));             // 99
        System.out.println(total(980, 3));        // 3234
        System.out.println(total(980, 3, 440));   // 2750
        System.out.println(total(100, 1, 500));   // 0
    }
}
