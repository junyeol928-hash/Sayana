import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceFetcherTest {

    private static final Map<String, Integer> API = Map.of("A001", 120, "B002", 250, "C003", 80);

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("結果は ids と同じ順番(先に終わった順ではない)")
    void keepsOrder() {
        // 先頭ほど時間がかかるので、終わる順番は ids と逆になる
        Map<String, Long> delay = Map.of("A001", 300L, "B002", 150L, "C003", 0L);
        Function<String, Integer> slowApi = id -> {
            sleep(delay.get(id));
            return API.get(id);
        };
        assertEquals(List.of(120, 250, 80), PriceFetcher.fetchAll(List.of("A001", "B002", "C003"), slowApi),
                "結果の順番が違います。全部 submit して Future をリストにため、そのリストの順に get してください");
    }

    @Test
    @DisplayName("空のリストなら空のリストを返す")
    void empty() {
        assertEquals(List.of(), PriceFetcher.fetchAll(List.of(), API::get),
                "ids が空なら空のリストを返してください(newFixedThreadPool(0) は IllegalArgumentException になります)");
    }

    @Test
    @DisplayName("lookup はすべて同時に(呼び出し元とは別のスレッドで)走る")
    void runsConcurrently() {
        int n = 3;
        CountDownLatch allStarted = new CountDownLatch(n);
        Set<Thread> threads = ConcurrentHashMap.newKeySet();
        // 3件そろって呼ばれるまで待つ API。1件ずつ順番に呼ぶと、そろわないので -1 を返す
        Function<String, Integer> api = id -> {
            threads.add(Thread.currentThread());
            allStarted.countDown();
            try {
                return allStarted.await(800, TimeUnit.MILLISECONDS) ? API.get(id) : -1;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        };
        List<Integer> result;
        try {
            result = PriceFetcher.fetchAll(List.of("A001", "B002", "C003"), api);
        } catch (IllegalStateException e) {
            throw new AssertionError("3件を同時に呼べていません。newFixedThreadPool(ids.size()) で、全部 submit してから get してください", e);
        }
        assertFalse(threads.contains(Thread.currentThread()),
                "lookup が呼び出し元のスレッドで実行されています。ExecutorService に submit してください");
        assertEquals(List.of(120, 250, 80), result,
                "3件の lookup が同時に走っていません(-1 は「ほかの呼び出しを待てなかった」印)。submit のたびに get していませんか?");
    }

    @Test
    @DisplayName("1秒以内に返ってこなければ IllegalStateException(タイムアウト付きの get)")
    void timesOut() {
        Function<String, Integer> hangingApi = id -> {
            if (id.equals("B002")) {
                sleep(3_000); // 返事をしない外部API
            }
            return API.get(id);
        };
        long start = System.nanoTime();
        assertThrows(IllegalStateException.class,
                () -> PriceFetcher.fetchAll(List.of("A001", "B002"), hangingApi),
                "応答しない API を待ち続けています。get(1, TimeUnit.SECONDS) でタイムアウトを付け、失敗したら IllegalStateException を投げてください");
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(millis < 2_500,
                "諦めるまでに " + millis + " ミリ秒かかりました。get の待ち時間を1秒にしてください");
    }

    @Test
    @DisplayName("終わったらプールのスレッドが止まっている(shutdown 済み)")
    void shutsDownPool() throws InterruptedException {
        Set<Thread> workers = ConcurrentHashMap.newKeySet();
        Function<String, Integer> api = id -> {
            workers.add(Thread.currentThread());
            return API.get(id);
        };
        assertEquals(List.of(120, 250), PriceFetcher.fetchAll(List.of("A001", "B002"), api),
                "A001 と B002 の価格 [120, 250] が返っていません");
        for (Thread t : workers) {
            t.join(2_000); // shutdown していればすぐ終わる。していなければプールのスレッドは残り続ける
            assertFalse(t.isAlive(),
                    "fetchAll が終わってもプールのスレッドが動き続けています。finally で pool.shutdownNow() を呼んでください");
        }
    }
}
