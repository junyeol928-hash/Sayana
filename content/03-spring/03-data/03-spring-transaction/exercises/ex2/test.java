import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceTest {

    /** DB の代わりに「開始・コミット・ロールバック」を記録するトランザクションマネージャ */
    static class RecordingTxManager extends AbstractPlatformTransactionManager {
        final List<String> log = new ArrayList<>();
        private final StockStore store;
        private Map<String, Integer> snapshot;

        RecordingTxManager(StockStore store) {
            this.store = store;
        }

        @Override
        protected Object doGetTransaction() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return (Boolean) transaction;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            log.add("begin");
            snapshot = store.snapshot();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            log.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            log.add("rollback");
            store.restore(snapshot); // DB のロールバックの代わりに、開始時の在庫に戻す
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            log.add("setRollbackOnly");
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        StockStore stockStore() {
            StockStore store = new StockStore();
            store.register("ノートPC", 9000, 5);
            store.register("マウス", 2000, 10);
            return store;
        }

        @Bean
        PaymentGateway paymentGateway() {
            return new PaymentGateway();
        }

        @Bean
        RecordingTxManager transactionManager(StockStore store) {
            return new RecordingTxManager(store);
        }
    }

    private AnnotationConfigApplicationContext ctx;
    private OrderService service;
    private StockStore stock;
    private List<String> log;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext(TestConfig.class, OrderService.class);
        service = ctx.getBean(OrderService.class);
        stock = ctx.getBean(StockStore.class);
        log = ctx.getBean(RecordingTxManager.class).log;
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    @DisplayName("正常な注文はコミットされ、在庫が減る(在庫ちょうどの数量も買える)")
    void placeOrderCommits() throws Exception {
        service.placeOrder("マウス", 5);   // 2000 × 5 = 10000 円(限度額ちょうど)
        assertEquals(List.of("begin", "commit"), log, "placeOrder はトランザクションの中で実行され、コミットされるはずです");
        assertEquals(5, stock.stockOf("マウス"), "マウスの在庫は 10 - 5 = 5 になるはずです");
        service.placeOrder("マウス", 5);
        assertEquals(0, stock.stockOf("マウス"), "残り在庫と同じ数量(5)は注文できるはずです");
    }

    @Test
    @DisplayName("在庫不足(RuntimeException)ならロールバックされる")
    void rollbackWhenOutOfStock() {
        assertThrows(IllegalStateException.class, () -> service.placeOrder("ノートPC", 6),
                "在庫 5 のノートPC を 6 台注文すると IllegalStateException になるはずです");
        assertEquals(List.of("begin", "rollback"), log, "RuntimeException ではロールバックされるはずです");
        assertEquals(5, stock.stockOf("ノートPC"), "在庫は 5 のままのはずです");
    }

    @Test
    @DisplayName("決済失敗(検査例外 PaymentException)でもロールバックされ、在庫が元に戻る")
    void rollbackOnCheckedException() {
        // 9000 × 2 = 18000 円で限度額オーバー。在庫を減らした「後」で決済に失敗する
        assertThrows(PaymentException.class, () -> service.placeOrder("ノートPC", 2),
                "決済失敗の PaymentException はそのまま呼び出し元に投げてください");
        assertEquals(List.of("begin", "rollback"), log,
                "検査例外は既定ではロールバックされず、コミットされてしまいます。"
                        + "@Transactional(rollbackFor = PaymentException.class) を指定してください");
        assertEquals(5, stock.stockOf("ノートPC"), "決済に失敗したので、ノートPC の在庫は 5 に戻るはずです");
    }

    @Test
    @DisplayName("placeAll は全体で1つのトランザクションとして実行される")
    void placeAllIsOneTransaction() throws Exception {
        service.placeAll(List.of(new OrderLine("ノートPC", 1), new OrderLine("マウス", 3)));
        assertEquals(List.of("begin", "commit"), log,
                "placeAll 全体で begin と commit が1回ずつのはずです。同じクラスの中から this.placeOrder() を呼んでも"
                        + "プロキシを通らないので、placeOrder の @Transactional は効きません。placeAll 自身に @Transactional を付けてください");
        assertEquals(4, stock.stockOf("ノートPC"), "ノートPC の在庫は 4 になるはずです");
        assertEquals(7, stock.stockOf("マウス"), "マウスの在庫は 7 になるはずです");
    }

    @Test
    @DisplayName("placeAll の途中で決済に失敗したら、成功済みの行の在庫も含めて全部元に戻る")
    void placeAllRollsBackEverything() {
        // 1行目(マウス 1 個 = 2000 円)は成功、2行目(ノートPC 2 台 = 18000 円)で決済失敗
        assertThrows(PaymentException.class,
                () -> service.placeAll(List.of(new OrderLine("マウス", 1), new OrderLine("ノートPC", 2))),
                "2行目の決済失敗の PaymentException は、そのまま呼び出し元に投げてください");
        assertEquals(List.of("begin", "rollback"), log,
                "placeAll に @Transactional(rollbackFor = PaymentException.class) を付けて、1つのトランザクションでロールバックしてください");
        assertEquals(10, stock.stockOf("マウス"), "1行目で減らしたマウスの在庫も 10 に戻るはずです");
        assertEquals(5, stock.stockOf("ノートPC"), "ノートPC の在庫は 5 のままのはずです");
    }
}
