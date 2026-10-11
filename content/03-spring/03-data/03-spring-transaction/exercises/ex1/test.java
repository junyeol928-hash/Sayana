import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferServiceTest {

    /** DB の代わりに「開始・コミット・ロールバック」を記録するトランザクションマネージャ */
    static class RecordingTxManager extends AbstractPlatformTransactionManager {
        final List<String> log = new ArrayList<>();
        private final AccountStore store;
        private Map<String, Integer> snapshot;

        RecordingTxManager(AccountStore store) {
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
            log.add(definition.isReadOnly() ? "begin(readOnly)" : "begin");
            snapshot = store.snapshot();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            log.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            log.add("rollback");
            store.restore(snapshot); // DB のロールバックの代わりに、開始時の状態に戻す
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
        AccountStore accountStore() {
            AccountStore store = new AccountStore();
            store.open("A", 10000);
            store.open("B", 0);
            return store;
        }

        @Bean
        RecordingTxManager transactionManager(AccountStore store) {
            return new RecordingTxManager(store);
        }
    }

    private AnnotationConfigApplicationContext ctx;
    private TransferService service;
    private AccountStore store;
    private List<String> log;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext(TestConfig.class, TransferService.class);
        service = ctx.getBean(TransferService.class);
        store = ctx.getBean(AccountStore.class);
        log = ctx.getBean(RecordingTxManager.class).log;
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    @DisplayName("正常な振込は1つのトランザクションでコミットされ、残高が移動する")
    void transferCommits() {
        service.transfer("A", "B", 3000);
        assertTrue(AopUtils.isAopProxy(service),
                "TransferService がプロキシになっていません。transfer に @Transactional を付けてください");
        assertEquals(List.of("begin", "commit"), log,
                "transfer はトランザクションを開始して(begin)、最後にコミット(commit)されるはずです");
        assertEquals(7000, store.balanceOf("A"), "振込元 A の残高は 10000 - 3000 = 7000 になるはずです");
        assertEquals(3000, store.balanceOf("B"), "振込先 B の残高は 0 + 3000 = 3000 になるはずです");
    }

    @Test
    @DisplayName("残高ちょうどの金額(10000円)なら振り込める(境界値)")
    void transferWholeBalance() {
        service.transfer("A", "B", 10000);
        assertEquals(0, store.balanceOf("A"), "残高と同じ金額は振り込めるはずです(A は 0 円になる)");
        assertEquals(10000, store.balanceOf("B"), "B に 10000 円入金されるはずです");
    }

    @Test
    @DisplayName("振込先の口座がないと、引き出し済みの分もロールバックされて元に戻る")
    void rollbackWhenDestinationMissing() {
        assertThrows(IllegalArgumentException.class, () -> service.transfer("A", "Z", 3000),
                "存在しない口座 Z への振込は IllegalArgumentException になるはずです(例外を握りつぶしていませんか)");
        assertEquals(List.of("begin", "rollback"), log,
                "途中で RuntimeException が起きたら、トランザクションはロールバックされるはずです");
        assertEquals(10000, store.balanceOf("A"),
                "ロールバックにより、A から引き出した 3000 円は元に戻るはずです(A = 10000)");
    }

    @Test
    @DisplayName("残高不足ならロールバックされ、どちらの残高も変わらない")
    void rollbackWhenInsufficient() {
        assertThrows(IllegalStateException.class, () -> service.transfer("A", "B", 10001),
                "残高 10000 円から 10001 円は振り込めず、IllegalStateException になるはずです");
        assertEquals(List.of("begin", "rollback"), log, "残高不足のときはロールバックされるはずです");
        assertEquals(10000, store.balanceOf("A"), "A の残高は変わらないはずです");
        assertEquals(0, store.balanceOf("B"), "B の残高は変わらないはずです");
    }

    @Test
    @DisplayName("0円以下の振込は IllegalArgumentException で、残高は変わらない")
    void rejectNonPositiveAmount() {
        assertThrows(IllegalArgumentException.class, () -> service.transfer("A", "B", 0),
                "金額 0 の振込は IllegalArgumentException を投げてください");
        assertThrows(IllegalArgumentException.class, () -> service.transfer("A", "B", -500),
                "マイナスの金額の振込は IllegalArgumentException を投げてください(-500 円の振込は逆向きの送金になってしまいます)");
        assertEquals(10000, store.balanceOf("A"), "A の残高は変わらないはずです");
        assertEquals(0, store.balanceOf("B"), "B の残高は変わらないはずです");
    }

    @Test
    @DisplayName("残高照会 balanceOf は readOnly のトランザクションで実行される")
    void balanceOfIsReadOnly() {
        assertEquals(10000, service.balanceOf("A"), "balanceOf(\"A\") は 10000 を返すはずです");
        assertEquals(List.of("begin(readOnly)", "commit"), log,
                "balanceOf には @Transactional(readOnly = true) を付けてください");
    }
}
