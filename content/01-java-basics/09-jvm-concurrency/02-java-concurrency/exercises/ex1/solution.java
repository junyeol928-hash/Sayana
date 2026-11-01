import java.util.concurrent.atomic.AtomicInteger;

public class SafeCounter {
    private final AtomicInteger count = new AtomicInteger();

    public void increment() {
        count.incrementAndGet(); // 読む・足す・書くが割り込まれない1操作
    }

    public void add(int n) {
        count.addAndGet(n);
    }

    public int get() {
        return count.get();
    }

    public static void main(String[] args) throws InterruptedException {
        SafeCounter c = new SafeCounter();
        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < 10_000; j++) c.increment();
            });
            workers[i].start();
        }
        for (Thread w : workers) w.join();
        System.out.println(c.get()); // 40000
    }
}
