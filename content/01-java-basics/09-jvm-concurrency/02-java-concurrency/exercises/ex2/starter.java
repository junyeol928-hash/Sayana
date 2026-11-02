import java.util.List;
import java.util.function.Function;

public class CartService {
    private final Function<String, Integer> priceTable; // DI された部品のつもり(final なので安全)
    private int sum; // TODO: 全スレッドで共有される危険なフィールド。ローカル変数に移す

    public CartService(Function<String, Integer> priceTable) {
        this.priceTable = priceTable;
    }

    public int total(List<String> items) {
        sum = 0;
        for (String item : items) {
            int price = priceTable.apply(item); // 単価を引く(外部の価格表を見るので少し時間がかかる)
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
