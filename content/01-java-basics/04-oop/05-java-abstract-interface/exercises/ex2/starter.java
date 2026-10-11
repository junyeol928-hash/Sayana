// 割引ルールの約束(完成済み)
interface PriceRule {
    int apply(int price);

    default String label() {
        return "通常価格";
    }
}

// 割引なし(完成済み。label() は default のまま使う)
class NoDiscount implements PriceRule {
    public int apply(int price) {
        return price;
    }
}

class RateDiscount implements PriceRule {
    private final int percent;

    RateDiscount(int percent) {
        this.percent = percent;
    }

    public int apply(int price) {
        // TODO: 割引額 price * percent / 100 を引いた金額を返す
        return 0;
    }

    @Override
    public String label() {
        // TODO: "10%引き" の形式で返す
        return "";
    }
}

class CouponDiscount implements PriceRule {
    private final int yen;

    CouponDiscount(int yen) {
        this.yen = yen;
    }

    public int apply(int price) {
        // TODO: price - yen を返す。ただし 0 未満なら 0
        return 0;
    }

    @Override
    public String label() {
        // TODO: "500円引き" の形式で返す
        return "";
    }
}

public class Checkout {
    private final PriceRule rule;   // 型はインターフェース

    Checkout(PriceRule rule) {
        this.rule = rule;
    }

    int total(int[] prices) {
        // TODO: prices を合計してから rule.apply を1回だけ適用して返す
        return 0;
    }

    String receipt(int[] prices) {
        // TODO: "10%引き: 3000円 → 2700円" の形式で返す
        return "";
    }

    public static void main(String[] args) {
        int[] cart = {1000, 2000};
        System.out.println(new Checkout(new NoDiscount()).receipt(cart));
        System.out.println(new Checkout(new RateDiscount(10)).receipt(cart));
        System.out.println(new Checkout(new CouponDiscount(500)).receipt(cart));
    }
}
