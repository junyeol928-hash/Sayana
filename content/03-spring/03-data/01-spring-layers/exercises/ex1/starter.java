import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 業務ロジック(Service 層) */
public class OrderService {

    // TODO: OrderRepository 型の final フィールドを用意し、コンストラクタで受け取る
    public OrderService(OrderRepository repository) {
    }

    public Order placeOrder(String customer, int unitPrice, int quantity) {
        // TODO: 顧客名・数量のチェック(IllegalArgumentException)
        // TODO: 小計・送料(5000円以上は0円、未満は500円)・合計を計算
        // TODO: new Order(0, ...) を repository.save して、その戻り値を返す
        return null;
    }
}

/** 注文(id が 0 なら未保存) */
record Order(long id, String customer, int subtotal, int shipping, int total) {
    Order withId(long newId) {
        return new Order(newId, customer, subtotal, shipping, total);
    }
}

/** 永続化(Repository 層)のインターフェース */
interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(long id);
    List<Order> findAll();
}

/** テストや試作で使うメモリ上の実装 */
class InMemoryOrderRepository implements OrderRepository {

    private final List<Order> orders = new ArrayList<>();
    private long nextId = 1;

    @Override
    public Order save(Order order) {
        // TODO: id が 0 なら nextId を振って(withId)、リストに追加して返す
        return order;
    }

    @Override
    public Optional<Order> findById(long id) {
        // TODO: id が一致する注文を探す
        return Optional.empty();
    }

    @Override
    public List<Order> findAll() {
        // TODO: 保存した順の一覧を返す(List.copyOf が便利)
        return List.of();
    }
}

/** 入出力(Controller 層)。今は全部入りの Fat Controller */
class OrderController {

    private final OrderService service;

    // ↓ 切り出す前のコードが使っていた保存先。切り出したら不要になる
    private final List<Order> orders = new ArrayList<>();
    private long nextId = 1;

    OrderController(OrderService service) {
        this.service = service;
    }

    String order(String customer, int unitPrice, int quantity) {
        // TODO: 下の処理を OrderService / InMemoryOrderRepository に移し、ここでは service.placeOrder を呼ぶだけにする
        //       (IllegalArgumentException は catch して "エラー:" + メッセージ を返す)
        if (customer == null || customer.isBlank()) {
            return "エラー:顧客名が空です";
        }
        if (quantity < 1) {
            return "エラー:数量は1以上にしてください";
        }
        int subtotal = unitPrice * quantity;
        int shipping = subtotal >= 5000 ? 0 : 500;
        Order order = new Order(nextId++, customer, subtotal, shipping, subtotal + shipping);
        orders.add(order);
        return "注文番号 " + order.id() + ":合計 " + order.total() + " 円";
    }
}
