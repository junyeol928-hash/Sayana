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

@Service
public class OrderService {

    private final TaxPolicy taxPolicy;
    private final OrderRepository orderRepository;

    public OrderService(TaxPolicy taxPolicy, OrderRepository orderRepository) {
        this.taxPolicy = taxPolicy;
        this.orderRepository = orderRepository;
    }

    public int placeOrder(String orderId, int unitPrice, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("数量は1以上で指定してください: " + quantity);
        }
        int total = taxPolicy.withTax(unitPrice * quantity);
        orderRepository.save(orderId, total);
        return total;
    }
}
