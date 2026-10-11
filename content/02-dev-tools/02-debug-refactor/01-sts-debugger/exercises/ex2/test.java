import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReorderServiceTest {

    private static List<String> list(String... items) {
        return new ArrayList<>(List.of(items));
    }

    @Test
    @DisplayName("商品を追加すると注文に入る")
    void addItem() {
        Order order = new Order("佐藤", list("コピー用紙"));
        order.addItem("トナー");
        assertEquals(List.of("コピー用紙", "トナー"), order.getItems(),
                "addItem(\"トナー\") で商品が追加されていません。"
                + "addItem の if の条件が true になるのはどんなときか、Expressions ビューで確かめてください");
    }

    @Test
    @DisplayName("null や空白だけの商品名は無視する")
    void ignoreBlank() {
        Order order = new Order("佐藤", list("コピー用紙"));
        order.addItem(null);
        order.addItem("   ");
        assertEquals(List.of("コピー用紙"), order.getItems(),
                "null や空白だけの商品名は追加しないでください");
    }

    @Test
    @DisplayName("再注文すると前回と同じ商品が入る")
    void reorderCopiesItems() {
        Order previous = new Order("佐藤", list("コピー用紙", "トナー"));
        Order next = ReorderService.reorder(previous, "鈴木");
        assertEquals("鈴木", next.getCustomer(), "新しい注文の顧客名は引数の customer です");
        assertEquals(List.of("コピー用紙", "トナー"), next.getItems(),
                "再注文には前回と同じ商品が同じ順で入っている必要があります");
    }

    @Test
    @DisplayName("再注文に商品を足しても、前回の注文は変わらない")
    void reorderDoesNotChangePrevious() {
        Order previous = new Order("佐藤", list("コピー用紙", "トナー"));
        Order next = ReorderService.reorder(previous, "佐藤");
        next.addItem("ボールペン");
        assertEquals(List.of("コピー用紙", "トナー", "ボールペン"), next.getItems(),
                "今回の注文にボールペンが追加されていません");
        assertEquals(List.of("コピー用紙", "トナー"), previous.getItems(),
                "今回の注文に足したボールペンが、前回の注文にも入ってしまいました。"
                + "Variables ビューで previous と next の items の (id=…) を比べてみてください");
    }

    @Test
    @DisplayName("注文を作った後で元のリストを変えても、注文は変わらない")
    void constructorCopies() {
        List<String> source = list("コピー用紙");
        Order order = new Order("佐藤", source);
        source.add("トナー");
        assertEquals(List.of("コピー用紙"), order.getItems(),
                "Order を作った後に元のリストへ追加した商品が、注文にも入ってしまいました。"
                + "Order のコンストラクタで、受け取ったリストをコピーして持ってください");
    }
}
