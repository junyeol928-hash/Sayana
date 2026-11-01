import java.util.Arrays;
import java.util.Comparator;

class Product {
    final String name;
    final int price;

    Product(String name, int price) {
        this.name = name;
        this.price = price;
    }

    @Override
    public String toString() {
        return name + "(" + price + ")";
    }
}

public class ProductSorter {

    static Comparator<Product> byPriceDesc() {
        return new Comparator<Product>() {
            @Override
            public int compare(Product a, Product b) {
                // 高い順なので b と a を比べる
                int byPrice = Integer.compare(b.price, a.price);
                if (byPrice != 0) {
                    return byPrice;
                }
                // 同じ価格なら商品名の辞書順
                return a.name.compareTo(b.name);
            }
        };
    }

    static void sortForShelf(Product[] items) {
        Arrays.sort(items, byPriceDesc());
    }

    public static void main(String[] args) {
        Product[] items = {
            new Product("ノート", 120),
            new Product("ペン", 150),
            new Product("付箋", 120),
            new Product("ファイル", 300)
        };
        sortForShelf(items);
        System.out.println(Arrays.toString(items));
    }
}
