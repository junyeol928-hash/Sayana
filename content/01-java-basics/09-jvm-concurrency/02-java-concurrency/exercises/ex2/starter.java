import java.util.List;
import java.util.function.Function;

public class CartService {
    // DI された部品のつもり(final なので安全)
    private final Function<String, Integer> priceTable;
    // TODO: 全スレッドで共有される危険なフィールド。ローカル変数に移す
    private int sum;

    public CartService(Function<String, Integer> priceTable) {
        this.priceTable = priceTable;
    }

    public int total(List<String> items) {
        sum = 0;
        for (String item : items) {
            // 単価を引く(外部の価格表を見るので少し時間がかかる)
            int price = priceTable.apply(item);
            sum += price;
        }
        return sum;
    }

    public static void main(String[] args) {
        java.util.Map<String, Integer> prices = java.util.Map.of("ペン", 120, "ノート", 250);
        CartService service = new CartService(prices::get);
        // 出力: 490
        System.out.println(service.total(List.of("ペン", "ノート", "ペン")));
    }
}
