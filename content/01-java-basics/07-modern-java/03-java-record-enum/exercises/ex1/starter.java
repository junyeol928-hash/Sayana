public class TaxCalc {

    public static long priceWithTax(long price, TaxCategory category) {
        // TODO: price に category の税額を足した税込価格を返す
        return 0; // ← ダミー。書き換えてください
    }

    public static TaxCategory parse(String text) {
        // TODO: null なら IllegalArgumentException を投げる
        // TODO: 前後の空白を除き、大文字に揃えてから valueOf で変換する
        return null; // ← ダミー。書き換えてください
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
        // TODO: price * ratePercent / 100 を返す(1円未満は切り捨て)
        return 0; // ← ダミー。書き換えてください
    }
}
