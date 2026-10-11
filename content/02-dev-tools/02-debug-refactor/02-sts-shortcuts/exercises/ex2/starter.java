import java.util.List;

/** 請求明細の1行(商品名・単価・数量) */
record Item(String name, int unitPrice, int quantity) {
}

public class Invoice {

    /**
     * 請求書の文字列を作る。動作は正しいが、1つのメソッドに全部入っていて長い。
     * TODO: 下の4つのメソッドに処理を分け、build からはそれらを呼ぶように書き換える。
     *       出力される文字列は1文字も変えないこと。
     */
    public static String build(List<Item> items, boolean member) {
        // 小計
        int subtotal = 0;
        for (Item item : items) {
            subtotal += item.unitPrice() * item.quantity();
        }
        // 割引:会員は小計の10%、非会員でも小計1万円以上なら5%(端数切り捨て)
        int discount = 0;
        if (member) {
            discount = subtotal * 10 / 100;
        } else if (subtotal >= 10000) {
            discount = subtotal * 5 / 100;
        }
        // 消費税:割引後の金額の10%(端数切り捨て)
        int taxable = subtotal - discount;
        int tax = taxable * 10 / 100;
        // 文字列の組み立て
        StringBuilder sb = new StringBuilder();
        for (Item item : items) {
            sb.append(item.name() + " x" + item.quantity()
                    + " = " + item.unitPrice() * item.quantity());
            sb.append("\n");
        }
        sb.append("小計: " + subtotal + "\n");
        sb.append("割引: -" + discount + "\n");
        sb.append("税: " + tax + "\n");
        sb.append("合計: " + (taxable + tax));
        return sb.toString();
    }

    // ---- 分割後のメソッド(テストから直接呼ばれる)----

    /** 小計:単価 × 数量 の合計 */
    static int subtotal(List<Item> items) {
        // TODO: build の「小計」の部分をここに移す
        return -1;
    }

    /** 割引額 */
    static int discount(int subtotal, boolean member) {
        // TODO: build の「割引」の部分をここに移す
        return -1;
    }

    /** 消費税額(taxable は割引後の金額) */
    static int tax(int taxable) {
        // TODO: build の「消費税」の部分をここに移す
        return -1;
    }

    /** 明細1行の文字列(例:「ペン x3 = 360」) */
    static String line(Item item) {
        // TODO: build の明細1行を作る式をここに移す
        return "";
    }

    public static void main(String[] args) {
        List<Item> items = List.of(
                new Item("ペン", 120, 3),
                new Item("ノート", 250, 2));
        System.out.println(build(items, true));
    }
}
