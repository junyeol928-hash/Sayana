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
        return "[" + code + "] " + name + " " + price + "円";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Product)) {
            return false;
        }
        Product other = (Product) o;
        return code.equals(other.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
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
        return getPrice() * (100 - rate) / 100;
    }

    @Override
    public String toString() {
        return super.toString() + " → " + rate + "%OFF " + salePrice() + "円";
    }
}
