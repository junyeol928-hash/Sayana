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
                // TODO: 価格の高い方が前(負)になるように比べる
                // TODO: 価格が同じなら name の辞書順で比べる
                return 0;
            }
        };
    }

    static void sortForShelf(Product[] items) {
        // TODO: Arrays.sort(items, byPriceDesc()) で並べ替える
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
