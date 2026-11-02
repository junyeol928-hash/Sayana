public class SafeCounter {
    // TODO: 複数スレッドから同時に呼ばれると数え漏れが起きる。AtomicInteger に変える
    private int count = 0;

    public void increment() {
        count++; // 「読む・足す・書く」の3ステップ。途中で割り込まれる
    }

    public void add(int n) {
        // TODO: n 増やす
    }

    public int get() {
        return count;
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
        System.out.println(c.get()); // 40000 になってほしい
    }
}
