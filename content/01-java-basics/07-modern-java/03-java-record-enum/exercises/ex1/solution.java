public class TaxCalc {

    public static long priceWithTax(long price, TaxCategory category) {
        return price + category.taxOf(price);
    }

    public static TaxCategory parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("税区分が指定されていません");
        }
        // 一致しない名前なら valueOf が IllegalArgumentException を投げる
        return TaxCategory.valueOf(text.strip().toUpperCase());
    }

    public static void main(String[] args) {
        System.out.println(TaxCategory.STANDARD.taxOf(1000));       // 100
        System.out.println(TaxCategory.REDUCED.taxOf(99));          // 7
        System.out.println(priceWithTax(1000, TaxCategory.REDUCED)); // 1080
        System.out.println(parse(" reduced "));                     // REDUCED
    }
}

enum TaxCategory {
    STANDARD(10), REDUCED(8), EXEMPT(0);

    private final int ratePercent;

    TaxCategory(int ratePercent) {
        this.ratePercent = ratePercent;
    }

    int ratePercent() {
        return ratePercent;
    }

    long taxOf(long price) {
        return price * ratePercent / 100;
    }
}
