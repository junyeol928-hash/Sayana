import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class PriceFetcher {

    static List<Integer> fetchAll(List<String> ids, Function<String, Integer> lookup) {
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        ExecutorService pool = Executors.newFixedThreadPool(ids.size());
        try {
            // 先に全部投げてから待つ。投げるたびに待つと、結局1件ずつになる
            List<Future<Integer>> futures = new ArrayList<>();
            for (String id : ids) {
                futures.add(pool.submit(() -> lookup.apply(id)));
            }
            List<Integer> prices = new ArrayList<>();
            for (Future<Integer> f : futures) {
                prices.add(f.get(1, TimeUnit.SECONDS)); // 投げた順に受け取るので順番が保たれる
            }
            return prices;
        } catch (Exception e) {
            throw new IllegalStateException("価格の取得に失敗しました", e);
        } finally {
            pool.shutdownNow(); // 例外のときも必ず止める。まだ動いている仕事には中断を伝える
        }
    }

    public static void main(String[] args) {
        Map<String, Integer> api = Map.of("A001", 120, "B002", 250, "C003", 80);
        System.out.println(fetchAll(List.of("B002", "A001", "C003"), api::get)); // [250, 120, 80]
    }
}
