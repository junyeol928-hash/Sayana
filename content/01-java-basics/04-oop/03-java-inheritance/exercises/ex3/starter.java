public class Product {
    private final String code;
    private final String name;
    private final int price;

    Product(String code, String name, int price) {
        this.code = code;
        this.name = name;
        this.price = price;
    }

    String getCode() {
        return code;
    }

    int getPrice() {
        return price;
    }

    @Override
    public String toString() {
        // TODO: "[A001] りんご 120円" の形式で返す
        return "";
    }

    @Override
    public boolean equals(Object o) {
        // TODO: o が Product で、商品コードが同じなら true
        return false;
    }

    @Override
    public int hashCode() {
        // TODO: 商品コードの hashCode を返す
        return 0;
    }

    public static void main(String[] args) {
        Product apple = new Product("A001", "りんご", 120);
        System.out.println(apple);                                    // [A001] りんご 120円
        System.out.println(apple.equals(new Product("A001", "林檎", 150))); // true
        System.out.println(new DiscountProduct("B002", "みかん", 300, 20)); // [B002] みかん 300円 → 20%OFF 240円
    }
}

class DiscountProduct extends Product {
    private final int rate;

    DiscountProduct(String code, String name, int price, int rate) {
        super(code, name, price);
        this.rate = rate;
    }

    int salePrice() {
        // TODO: price × (100 - rate) / 100 を返す(price は getPrice() で取得)
        return 0;
    }

    // TODO: toString() をオーバーライドし、super.toString() + " → 20%OFF 240円" の形式で返す
}
