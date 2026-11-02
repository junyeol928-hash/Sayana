import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeCounterTest {

    @Test
    @DisplayName("1スレッドで increment と add が正しく数えられる")
    void singleThread() {
        SafeCounter c = new SafeCounter();
        assertEquals(0, c.get(), "作った直後の get() は 0 のはずです");
        c.increment();
        c.increment();
        assertEquals(2, c.get(), "increment() を2回呼んだら 2 になるはずです");
        c.add(5);
        assertEquals(7, c.get(), "2 のあと add(5) したら 7 になるはずです。add の TODO を実装しましたか?");
        c.add(0);
        assertEquals(7, c.get(), "add(0) では値が変わらないはずです");
    }

    @Test
    @DisplayName("スレッドセーフな仕組み(AtomicInteger か synchronized)で守っている")
    void usesThreadSafeMechanism() throws Exception {
        boolean atomicField = false;
        for (Field f : SafeCounter.class.getDeclaredFields()) {
            if (f.getType() == AtomicInteger.class || f.getType() == AtomicLong.class) {
                atomicField = true;
            }
        }
        Method inc = SafeCounter.class.getDeclaredMethod("increment");
        Method add = SafeCounter.class.getDeclaredMethod("add", int.class);
        Method get = SafeCounter.class.getDeclaredMethod("get");
        boolean allSynchronized = Modifier.isSynchronized(inc.getModifiers())
                && Modifier.isSynchronized(add.getModifiers())
                && Modifier.isSynchronized(get.getModifiers());
        assertTrue(atomicField || allSynchronized,
                "素の int の count++ は、同時に呼ばれると数え漏れが起きます(たまたまテストが通ることもあるので、仕組みで確かめています)。"
                        + "フィールドを AtomicInteger にするか、increment / add / get の3つすべてに synchronized を付けてください");
    }

    @Test
    @DisplayName("8本のスレッドが同時に1万回ずつ数えても 80000 になる")
    void manyThreads() throws Exception {
        SafeCounter c = new SafeCounter();
        int threads = 8;
        int perThread = 10_000;
        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.await(); // 全員そろってから一斉に数え始める
                    for (int j = 0; j < perThread; j++) {
                        c.increment();
                    }
                    return null;
                }));
            }
            ready.countDown();
            for (Future<?> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(threads * perThread, c.get(),
                "同時に数えると加算が消えています。count++ は割り込まれるので、AtomicInteger の incrementAndGet() を使ってください");
    }

    @Test
    @DisplayName("increment と add を同時に混ぜて呼んでも合計が合う")
    void mixedCalls() throws Exception {
        SafeCounter c = new SafeCounter();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    for (int j = 0; j < 5_000; j++) {
                        c.increment();
                        c.add(2);
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(4 * 5_000 * 3, c.get(),
                "add(n) も同時に呼ばれます。add も addAndGet(n) のように割り込まれない操作にしてください");
    }
}
