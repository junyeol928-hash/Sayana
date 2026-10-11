import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

class Product {
    private final String name;
    private final int sold;

    Product(String name, int sold) {
        this.name = name;
        this.sold = sold;
    }

    String getName() { return name; }
    int getSold() { return sold; }
}

public class SalesRanking {

    public static String topSellers(List<Product> products, int n) {
        if (n <= 0) {
            return "";
        }
        return products.stream()
                .filter(p -> p.getSold() > 0)
                .sorted(Comparator.comparing(Product::getSold).reversed()
                        .thenComparing(Product::getName))
                .limit(n)
                .map(Product::getName)
                .collect(Collectors.joining(", "));
    }

    public static void main(String[] args) {
        List<Product> products = List.of(
                new Product("マグカップ", 12),
                new Product("トートバッグ", 30),
                new Product("ボールペン", 12),
                new Product("ノート", 0),
                new Product("付箋", 7));
        System.out.println(topSellers(products, 3));  // トートバッグ, ボールペン, マグカップ
        System.out.println(topSellers(products, 10)); // トートバッグ, ボールペン, マグカップ, 付箋
        System.out.println("[" + topSellers(products, 0) + "]"); // []
    }
}
