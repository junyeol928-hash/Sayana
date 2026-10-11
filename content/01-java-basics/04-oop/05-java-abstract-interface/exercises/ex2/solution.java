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
        return price - price * percent / 100;
    }

    @Override
    public String label() {
        return percent + "%引き";
    }
}

class CouponDiscount implements PriceRule {
    private final int yen;

    CouponDiscount(int yen) {
        this.yen = yen;
    }

    public int apply(int price) {
        return Math.max(0, price - yen);
    }

    @Override
    public String label() {
        return yen + "円引き";
    }
}

public class Checkout {
    private final PriceRule rule;   // 型はインターフェース

    Checkout(PriceRule rule) {
        this.rule = rule;
    }

    int total(int[] prices) {
        int sum = 0;
        for (int p : prices) {
            sum += p;
        }
        return rule.apply(sum);
    }

    String receipt(int[] prices) {
        int sum = 0;
        for (int p : prices) {
            sum += p;
        }
        return rule.label() + ": " + sum + "円 → " + rule.apply(sum) + "円";
    }

    public static void main(String[] args) {
        int[] cart = {1000, 2000};
        System.out.println(new Checkout(new NoDiscount()).receipt(cart));
        System.out.println(new Checkout(new RateDiscount(10)).receipt(cart));
        System.out.println(new Checkout(new CouponDiscount(500)).receipt(cart));
    }
}
