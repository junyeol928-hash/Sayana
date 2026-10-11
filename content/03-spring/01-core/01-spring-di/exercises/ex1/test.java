import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderServiceTest {

    private AnnotationConfigApplicationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext(
                TenPercentTax.class, InMemoryOrderRepository.class, OrderService.class);
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    @DisplayName("コンテナから取り出した OrderService で、1000円×3個 → 税込3300円")
    void placeOrderFromContainer() {
        OrderService service = ctx.getBean(OrderService.class);
        assertEquals(3300, service.placeOrder("A001", 1000, 3),
                "1000円×3個の税込は3300円です。コンストラクタでフィールドに代入しているか、taxPolicy.withTax を使っているか確認してください");
    }

    @Test
    @DisplayName("注文がリポジトリに保存される(コンテナが同じ Bean を配線している)")
    void savedToRepository() {
        ctx.getBean(OrderService.class).placeOrder("A001", 1000, 3);
        ctx.getBean(OrderService.class).placeOrder("A002", 250, 1);
        Map<String, Integer> saved = ctx.getBean(OrderRepository.class).findAll();
        assertEquals(Map.of("A001", 3300, "A002", 275), saved,
                "orderRepository.save(orderId, 税込金額) で保存してください");
    }

    @Test
    @DisplayName("Spring なしでも new で偽物の部品を渡してテストできる")
    void worksWithoutSpring() {
        Map<String, Integer> fakeStore = new HashMap<>();
        OrderRepository fakeRepo = new OrderRepository() {
            @Override
            public void save(String orderId, int total) {
                fakeStore.put(orderId, total);
            }

            @Override
            public Map<String, Integer> findAll() {
                return fakeStore;
            }
        };
        TaxPolicy noTax = amount -> amount; // 非課税の偽物
        OrderService service = new OrderService(noTax, fakeRepo);
        assertEquals(500, service.placeOrder("T1", 100, 5),
                "渡された TaxPolicy を使って計算してください(TenPercentTax を直接 new していませんか?)");
        assertEquals(Map.of("T1", 500), fakeStore, "渡された OrderRepository に保存してください");
    }

    @Test
    @DisplayName("数量0は IllegalArgumentException で、保存もされない。数量1はOK(境界値)")
    void quantityBoundary() {
        OrderService service = ctx.getBean(OrderService.class);
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder("X", 1000, 0),
                "数量が 0 以下のときは IllegalArgumentException を投げてください");
        assertTrue(ctx.getBean(OrderRepository.class).findAll().isEmpty(),
                "数量が不正な注文は保存しないでください(チェックは保存より前に)");
        assertEquals(1100, service.placeOrder("A003", 1000, 1), "数量1は正常な注文です");
    }

    @Test
    @DisplayName("@Service が付いていて、依存フィールドが final になっている")
    void annotationAndFinalFields() throws Exception {
        assertTrue(OrderService.class.isAnnotationPresent(Service.class),
                "OrderService クラスに @Service を付けてください");
        for (String name : new String[] {"taxPolicy", "orderRepository"}) {
            Field f = OrderService.class.getDeclaredField(name);
            assertTrue(Modifier.isFinal(f.getModifiers()),
                    "フィールド " + name + " に final を付けてください(コンストラクタインジェクションなら付けられます)");
        }
    }
}
