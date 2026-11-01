import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CartServiceTest {

    private static final Map<String, Integer> PRICES = Map.of("ペン", 120, "ノート", 250, "消しゴム", 80);

    @Test
    @DisplayName("1人で使えば合計が正しい(ペン+ノート+ペン = 490円)")
    void singleUser() {
        CartService service = new CartService(PRICES::get);
        assertEquals(490, service.total(List.of("ペン", "ノート", "ペン")), "120 + 250 + 120 = 490 になるはずです");
        assertEquals(80, service.total(List.of("消しゴム")), "2回目の計算に1回目の合計が残っていないか確認してください");
        assertEquals(0, service.total(List.of()), "空のカートの合計は 0 のはずです");
    }

    @Test
    @DisplayName("書き換え可能なインスタンスフィールドを持たない(final だけ)")
    void noMutableFields() {
        for (Field f : CartService.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())) {
                continue; // 定数は問題ない
            }
            assertTrue(Modifier.isFinal(f.getModifiers()),
                    "フィールド " + f.getName() + " が書き換え可能です。シングルトンは全スレッドで共有されるので、"
                            + "計算途中の値はメソッドのローカル変数(int sum = 0;)に持ってください");
        }
    }

    @Test
    @DisplayName("Aさんの計算の途中でBさんが計算しても、Aさんの合計が狂わない")
    void interleavedUsers() throws Exception {
        CountDownLatch aPaused = new CountDownLatch(1);
        CountDownLatch bDone = new CountDownLatch(1);
        // 「一時停止」という商品の単価を引くとき、Aさんのスレッドだけを止めて、その間にBさんに計算させる
        CartService service = new CartService(item -> {
            if (item.equals("一時停止")) {
                aPaused.countDown();
                try {
                    if (!bDone.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Bさんの計算が終わりませんでした");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return 0;
            }
            return PRICES.get(item);
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = pool.submit(() -> service.total(List.of("ノート", "一時停止", "ペン")));
            assertTrue(aPaused.await(3, TimeUnit.SECONDS), "Aさんの計算が「一時停止」まで進みませんでした");
            Future<Integer> b = pool.submit(() -> {
                try {
                    return service.total(List.of("消しゴム"));
                } finally {
                    bDone.countDown();
                }
            });
            int bTotal;
            try {
                bTotal = b.get(2, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                fail("Bさんの計算が、Aさんの計算が終わるまで待たされています。synchronized ではなく、"
                        + "ローカル変数に直して同時に計算できるようにしてください");
                return;
            }
            int aTotal = a.get(5, TimeUnit.SECONDS);
            assertEquals(80, bTotal, "Bさんの合計(消しゴム 80円)が違います");
            assertEquals(370, aTotal,
                    "Aさんの合計は 250 + 0 + 120 = 370 のはずが " + aTotal + " になりました。"
                            + "途中でBさんが共有のフィールド sum を 0 に戻して上書きしています。sum をローカル変数にしてください");
        } finally {
            bDone.countDown();
            pool.shutdownNow();
        }
    }
}
