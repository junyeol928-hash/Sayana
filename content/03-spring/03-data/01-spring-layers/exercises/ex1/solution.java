import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 業務ロジック(Service 層) */
public class OrderService {

    private static final int FREE_SHIPPING_THRESHOLD = 5000;
    private static final int SHIPPING_FEE = 500;

    private final OrderRepository repository;   // インターフェースに依存する

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order placeOrder(String customer, int unitPrice, int quantity) {
        if (customer == null || customer.isBlank()) {
            throw new IllegalArgumentException("顧客名が空です");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("数量は1以上にしてください");
        }
        int subtotal = unitPrice * quantity;
        int shipping = subtotal >= FREE_SHIPPING_THRESHOLD ? 0 : SHIPPING_FEE;
        return repository.save(new Order(0, customer, subtotal, shipping, subtotal + shipping));
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
        Order saved = order.id() == 0 ? order.withId(nextId++) : order;
        orders.add(saved);
        return saved;
    }

    @Override
    public Optional<Order> findById(long id) {
        return orders.stream().filter(o -> o.id() == id).findFirst();
    }

    @Override
    public List<Order> findAll() {
        return List.copyOf(orders);
    }
}

/** 入出力(Controller 層):受け取って、Service に任せて、結果を表示用に整えるだけ */
class OrderController {

    private final OrderService service;

    OrderController(OrderService service) {
        this.service = service;
    }

    String order(String customer, int unitPrice, int quantity) {
        try {
            Order order = service.placeOrder(customer, unitPrice, quantity);
            return "注文番号 " + order.id() + ":合計 " + order.total() + " 円";
        } catch (IllegalArgumentException e) {
            return "エラー:" + e.getMessage();
        }
    }
}
