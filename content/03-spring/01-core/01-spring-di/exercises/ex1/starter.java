import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

/** 税込金額を計算する部品 */
interface TaxPolicy {
    int withTax(int amount);
}

@Component
class TenPercentTax implements TaxPolicy {
    @Override
    public int withTax(int amount) {
        return amount * 110 / 100;
    }
}

/** 注文を保存する部品(本物なら DB。ここではメモリに保存) */
interface OrderRepository {
    void save(String orderId, int total);

    Map<String, Integer> findAll();
}

@Repository
class InMemoryOrderRepository implements OrderRepository {
    private final Map<String, Integer> store = new LinkedHashMap<>();

    @Override
    public void save(String orderId, int total) {
        store.put(orderId, total);
    }

    @Override
    public Map<String, Integer> findAll() {
        return Map.copyOf(store);
    }
}

// TODO: このクラスに @Service を付ける
public class OrderService {

    // TODO: 2つのフィールドに final を付ける
    private TaxPolicy taxPolicy;
    private OrderRepository orderRepository;

    public OrderService(TaxPolicy taxPolicy, OrderRepository orderRepository) {
        // TODO: 受け取った部品をフィールドに代入する
    }

    public int placeOrder(String orderId, int unitPrice, int quantity) {
        // TODO: quantity が 0 以下なら IllegalArgumentException を投げる

        // TODO: 小計を taxPolicy で税込にし、orderRepository に保存して、税込金額を返す

        return 0; // ← ダミー。書き換えてください
    }
}
