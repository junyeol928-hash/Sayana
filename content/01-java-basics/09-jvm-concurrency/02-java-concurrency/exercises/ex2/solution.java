import java.util.List;
import java.util.function.Function;

public class CartService {
    private final Function<String, Integer> priceTable; // 不変な依存だけを持つ

    public CartService(Function<String, Integer> priceTable) {
        this.priceTable = priceTable;
    }

    public int total(List<String> items) {
        int sum = 0; // ローカル変数はスレッドごとのスタックにあるので混ざらない
        for (String item : items) {
            int price = priceTable.apply(item);
            sum += price;
        }
        return sum;
    }

    public static void main(String[] args) {
        java.util.Map<String, Integer> prices = java.util.Map.of("ペン", 120, "ノート", 250);
        CartService service = new CartService(prices::get);
        System.out.println(service.total(List.of("ペン", "ノート", "ペン"))); // 490
    }
}
