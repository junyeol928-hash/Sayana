import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderServiceTest {

    /** テスト専用の Repository:保存されたものを記録し、番号は 100 から振る */
    static class RecordingRepository implements OrderRepository {
        final List<Order> saved = new ArrayList<>();

        @Override
        public Order save(Order order) {
            Order o = order.withId(100 + saved.size());
            saved.add(o);
            return o;
        }

        @Override
        public Optional<Order> findById(long id) {
            return saved.stream().filter(o -> o.id() == id).findFirst();
        }

        @Override
        public List<Order> findAll() {
            return List.copyOf(saved);
        }
    }

    @Test
    @DisplayName("小計・送料・合計を計算し、番号付きで保存された注文を返す")
    void placeOrder() {
        InMemoryOrderRepository repo = new InMemoryOrderRepository();
        OrderService service = new OrderService(repo);
        Order o = service.placeOrder("山田", 1200, 3);
        assertNotNull(o, "placeOrder は保存した注文を返してください(今は null)");
        assertEquals(3600, o.subtotal(), "小計は 単価 × 数量 です");
        assertEquals(500, o.shipping(), "小計が 5000 円未満なら送料は 500 円です");
        assertEquals(4100, o.total(), "合計は 小計 + 送料 です");
        assertEquals(1, o.id(), "最初に保存した注文の番号は 1 です。repository.save の戻り値を返していますか?");
        assertEquals(Optional.of(o), repo.findById(1), "保存した注文が findById(1) で見つかりません。InMemoryOrderRepository を確認してください");
    }

    @Test
    @DisplayName("送料の境界値:小計 4999 円は 500 円、5000 円ちょうどは無料")
    void shippingBoundary() {
        OrderService service = new OrderService(new InMemoryOrderRepository());
        Order under = service.placeOrder("山田", 4999, 1);
        assertNotNull(under, "placeOrder は保存した注文を返してください");
        assertEquals(500, under.shipping(), "小計 4999 円は送料 500 円です");
        Order just = service.placeOrder("山田", 2500, 2);
        assertEquals(0, just.shipping(), "小計 5000 円ちょうどは送料無料です(5000 円「以上」)");
        assertEquals(5000, just.total(), "送料無料なら合計は小計と同じです");
    }

    @Test
    @DisplayName("メモリ実装:番号は 1, 2, 3 と順に振られ、findAll は保存順")
    void inMemoryRepository() {
        InMemoryOrderRepository repo = new InMemoryOrderRepository();
        Order a = repo.save(new Order(0, "A", 100, 500, 600));
        Order b = repo.save(new Order(0, "B", 200, 500, 700));
        Order c = repo.save(new Order(0, "C", 300, 500, 800));
        assertEquals(List.of(1L, 2L, 3L), List.of(a.id(), b.id(), c.id()), "id が 0 の注文には 1 から順に番号を振ってください");
        assertEquals(List.of(a, b, c), repo.findAll(), "findAll は保存した順の一覧を返してください");
        assertEquals(Optional.empty(), repo.findById(99), "無い番号の findById は Optional.empty() を返してください");
    }

    @Test
    @DisplayName("入力エラー:数量 0・顧客名が空白なら例外を投げ、何も保存しない")
    void invalidInput() {
        RecordingRepository repo = new RecordingRepository();
        OrderService service = new OrderService(repo);
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> service.placeOrder("山田", 1000, 0), "数量 0 なら IllegalArgumentException を投げてください");
        assertEquals("数量は1以上にしてください", e1.getMessage(), "例外メッセージが違います");
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> service.placeOrder("  ", 1000, 1), "顧客名が空白だけなら IllegalArgumentException を投げてください");
        assertEquals("顧客名が空です", e2.getMessage(), "例外メッセージが違います");
        assertTrue(repo.saved.isEmpty(), "入力エラーなのに保存されています。チェックは保存より先に行ってください");
    }

    @Test
    @DisplayName("Service は渡された Repository を使う(差し替えられる)")
    void usesGivenRepository() {
        RecordingRepository repo = new RecordingRepository();
        Order o = new OrderService(repo).placeOrder("佐藤", 1000, 2);
        assertEquals(1, repo.saved.size(),
                "コンストラクタで受け取った Repository に保存されていません。Service の中で new InMemoryOrderRepository() していませんか?");
        assertNotNull(o, "placeOrder は保存した注文を返してください");
        assertEquals(100, o.id(), "repository.save の『戻り値』(番号付きの注文)を返してください");
    }

    @Test
    @DisplayName("Controller は Service に任せて、結果を文字列に整えるだけ")
    void controllerDelegates() {
        RecordingRepository repo = new RecordingRepository();
        OrderController controller = new OrderController(new OrderService(repo));
        assertEquals("注文番号 100:合計 4100 円", controller.order("山田", 1200, 3),
                "Controller は service.placeOrder の結果(番号は Repository が振ったもの)を表示してください。自分で番号を振ったり保存したりしていませんか?");
        assertEquals(1, repo.saved.size(), "Controller 経由の注文が Repository に保存されていません");
        assertEquals("エラー:数量は1以上にしてください", controller.order("山田", 1200, 0),
                "IllegalArgumentException を catch して \"エラー:\" + メッセージ を返してください");
    }
}
