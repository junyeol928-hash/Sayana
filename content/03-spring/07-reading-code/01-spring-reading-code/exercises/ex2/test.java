import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

class KeyRotationServiceTest {

    /** DB の代わりに「開始・コミット・ロールバック」を記録するトランザクションマネージャ */
    static class RecordingTxManager extends AbstractPlatformTransactionManager {
        final List<String> log = new ArrayList<>();
        private final KeyStore store;
        private List<KeyRow> snapshot;

        RecordingTxManager(KeyStore store) {
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
            store.restore(snapshot); // DB のロールバックの代わりに、開始時の状態に戻す
        }
    }

    /** テスト用の暗号装置。down = true のときは通信エラーになる */
    static class FakeCrypto implements CryptoClient {
        boolean down;
        int seq = 100;

        @Override
        public String generateKey(String cardId) throws CryptoException {
            if (down) {
                throw new CryptoException("暗号装置に接続できません(タイムアウト)");
            }
            return "K-0" + (seq++);
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        KeyStore keyStore() {
            KeyStore store = new KeyStore();
            store.add("K-0001", "C001", "ACTIVE");
            return store;
        }

        @Bean
        FakeCrypto crypto() {
            return new FakeCrypto();
        }

        @Bean
        RecordingTxManager transactionManager(KeyStore store) {
            return new RecordingTxManager(store);
        }
    }

    private AnnotationConfigApplicationContext ctx;
    private KeyRotationService service;
    private KeyStore store;
    private FakeCrypto crypto;
    private List<String> log;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext(TestConfig.class, KeyRotationService.class);
        service = ctx.getBean(KeyRotationService.class);
        store = ctx.getBean(KeyStore.class);
        crypto = ctx.getBean(FakeCrypto.class);
        log = ctx.getBean(RecordingTxManager.class).log;
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    @DisplayName("暗号装置が動いていれば、新しい鍵が ACTIVE になりコミットされる")
    void rotateCommits() {
        assertTrue(AopUtils.isAopProxy(service),
                "KeyRotationService がプロキシになっていません。rotate の @Transactional を消していませんか");
        String newKeyId = service.rotate("C001");
        assertEquals("K-0100", newKeyId, "rotate は暗号装置が作った新しい鍵 ID(K-0100)を返すはずです");
        assertEquals(Optional.of("K-0100"), store.activeKeyOf("C001"), "C001 の ACTIVE な鍵は新しい K-0100 になるはずです");
        assertEquals(List.of("begin", "commit"), log, "成功したときは begin → commit のはずです");
    }

    @Test
    @DisplayName("暗号装置が失敗したら KeyRotationFailedException を投げる(原因は CryptoException)")
    void failureIsThrown() {
        crypto.down = true;
        try {
            String result = service.rotate("C001");
            fail("暗号装置が失敗したのに、rotate が例外を投げずに " + result + " を返しました。"
                    + "catch で握りつぶすと、呼び出し元も Spring も失敗に気づけません。KeyRotationFailedException を投げてください");
        } catch (KeyRotationFailedException e) {
            assertInstanceOf(CryptoException.class, e.getCause(),
                    "KeyRotationFailedException の原因(cause)に CryptoException を渡してください。"
                            + "渡さないとスタックトレースから Caused by が消え、ログから原因をたどれません");
        }
    }

    @Test
    @DisplayName("暗号装置が失敗したらロールバックされ、元の鍵が ACTIVE に戻る")
    void failureRollsBack() {
        crypto.down = true;
        try {
            service.rotate("C001");
        } catch (RuntimeException e) {
            // 例外の型は failureIsThrown で確かめる
        }
        assertEquals(List.of("begin", "rollback"), log,
                "暗号装置が失敗したのにトランザクションがコミットされています(実際: " + log + ")。"
                        + "例外を catch してメソッドが普通に終わるとコミットになります。"
                        + "また CryptoException は検査例外なので、そのまま投げても既定ではロールバックされません。RuntimeException の KeyRotationFailedException にして投げ直してください");
        assertEquals(Optional.of("K-0001"), store.activeKeyOf("C001"),
                "ロールバックにより、失効させた元の鍵 K-0001 が ACTIVE に戻るはずです。今のままだと C001 は有効な鍵が1つも無いカードになります");
    }
}
