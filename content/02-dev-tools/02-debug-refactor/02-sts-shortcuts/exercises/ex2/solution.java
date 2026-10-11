import java.util.List;

/** 請求明細の1行(商品名・単価・数量) */
record Item(String name, int unitPrice, int quantity) {
}

public class Invoice {

    /** 請求書の文字列を作る。各ステップは小さなメソッドに分けた */
    public static String build(List<Item> items, boolean member) {
        int subtotal = subtotal(items);
        int discount = discount(subtotal, member);
        int taxable = subtotal - discount;
        int tax = tax(taxable);
        StringBuilder sb = new StringBuilder();
        for (Item item : items) {
            sb.append(line(item));
            sb.append("\n");
        }
        sb.append("小計: " + subtotal + "\n");
        sb.append("割引: -" + discount + "\n");
        sb.append("税: " + tax + "\n");
        sb.append("合計: " + (taxable + tax));
        return sb.toString();
    }

    /** 小計:単価 × 数量 の合計 */
    static int subtotal(List<Item> items) {
        int subtotal = 0;
        for (Item item : items) {
            subtotal += item.unitPrice() * item.quantity();
        }
        return subtotal;
    }

    /** 割引額:会員は小計の10%、非会員でも小計1万円以上なら5%(端数切り捨て) */
    static int discount(int subtotal, boolean member) {
        int discount = 0;
        if (member) {
            discount = subtotal * 10 / 100;
        } else if (subtotal >= 10000) {
            discount = subtotal * 5 / 100;
        }
        return discount;
    }

    /** 消費税額:割引後の金額の10%(端数切り捨て) */
    static int tax(int taxable) {
        return taxable * 10 / 100;
    }

    /** 明細1行の文字列(例:「ペン x3 = 360」) */
    static String line(Item item) {
        return item.name() + " x" + item.quantity()
                + " = " + item.unitPrice() * item.quantity();
    }

    public static void main(String[] args) {
        List<Item> items = List.of(
                new Item("ペン", 120, 3),
                new Item("ノート", 250, 2));
        System.out.println(build(items, true));
    }
}
