import java.util.ArrayList;
import java.util.List;

/** 注文。顧客名と商品名のリストを持つ */
class Order {
    private final String customer;
    private final List<String> items;

    Order(String customer, List<String> items) {
        this.customer = customer;
        this.items = items;
    }

    String getCustomer() {
        return customer;
    }

    List<String> getItems() {
        return items;
    }

    /** 商品を1つ追加する。null や空白だけの商品名は無視する */
    void addItem(String item) {
        if (item == null || item.isBlank()) {
            items.add(item);
        }
    }
}

public class ReorderService {

    /**
     * 「前回と同じ内容で注文」ボタンの処理。
     * 前回の注文と同じ商品で、新しい注文を作る。前回の注文は変えてはいけない。
     *
     * このコードにはバグが2つある(ReorderService と Order のどこか)。
     */
    public static Order reorder(Order previous, String customer) {
        return new Order(customer, previous.getItems());
    }

    public static void main(String[] args) {
        List<String> items = new ArrayList<>(List.of("コピー用紙", "トナー"));
        Order previous = new Order("佐藤", items);
        Order next = reorder(previous, "佐藤");
        next.addItem("ボールペン");
        System.out.println("前回: " + previous.getItems()); // 期待: [コピー用紙, トナー]
        System.out.println("今回: " + next.getItems());     // 期待: [コピー用紙, トナー, ボールペン]
    }
}
