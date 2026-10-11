import java.util.ArrayList;
import java.util.List;

/** 注文。顧客名と商品名のリストを持つ */
class Order {
    private final String customer;
    private final List<String> items;

    Order(String customer, List<String> items) {
        this.customer = customer;
        // バグ1(参照の共有):受け取ったリストをそのまま持つと、
        // 呼び出し元や別の注文と同じ ArrayList を共有してしまう。コピーして持つ(防御的コピー)
        this.items = new ArrayList<>(items);
    }

    String getCustomer() {
        return customer;
    }

    List<String> getItems() {
        return items;
    }

    /** 商品を1つ追加する。null や空白だけの商品名は無視する */
    void addItem(String item) {
        // バグ2(条件の逆転):「無視する条件」で add していた
        if (item == null || item.isBlank()) {
            return;
        }
        items.add(item);
    }
}

public class ReorderService {

    /**
     * 「前回と同じ内容で注文」ボタンの処理。
     * 前回の注文と同じ商品で、新しい注文を作る。前回の注文は変えてはいけない。
     */
    public static Order reorder(Order previous, String customer) {
        return new Order(customer, previous.getItems());
    }

    public static void main(String[] args) {
        List<String> items = new ArrayList<>(List.of("コピー用紙", "トナー"));
        Order previous = new Order("佐藤", items);
        Order next = reorder(previous, "佐藤");
        next.addItem("ボールペン");
        System.out.println("前回: " + previous.getItems()); // [コピー用紙, トナー]
        System.out.println("今回: " + next.getItems());     // [コピー用紙, トナー, ボールペン]
    }
}
