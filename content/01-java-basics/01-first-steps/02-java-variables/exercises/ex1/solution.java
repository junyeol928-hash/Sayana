public class TaxCalculator {

    /**
     * 税込価格を返す(1円未満は切り捨て)。
     * @param price   税抜価格(円)
     * @param taxRate 税率(10% なら 0.10)
     */
    public static int priceWithTax(int price, double taxRate) {
        double total = price * (1 + taxRate); // int × double → double(拡大変換)
        return (int) Math.floor(total);       // double → int はキャストが必要(縮小変換)
    }
}
