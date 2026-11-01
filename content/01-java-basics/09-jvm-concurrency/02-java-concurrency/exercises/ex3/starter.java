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
        // TODO: 空なら空のリストを返す
        // TODO: newFixedThreadPool(ids.size()) を作り、全部の id を submit して Future をリストにためる
        // TODO: Future を順番に get(1, TimeUnit.SECONDS) して結果に入れる。失敗したら IllegalStateException
        // TODO: finally で shutdownNow() する
        return new ArrayList<>(); // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        Map<String, Integer> api = Map.of("A001", 120, "B002", 250, "C003", 80);
        System.out.println(fetchAll(List.of("B002", "A001", "C003"), api::get)); // [250, 120, 80]
    }
}
