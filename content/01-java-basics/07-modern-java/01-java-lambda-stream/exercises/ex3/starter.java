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
        // TODO: n が 0 以下なら "" を返す(limit に負の数を渡すと例外になるため)

        // TODO: products.stream() から
        //       ① 販売数 0 の商品を除く(filter)
        //       ② 販売数の多い順、同数なら名前順に並べる(sorted)
        //       ③ 先頭 n 件に絞る(limit)
        //       ④ 商品名に変換して(map)、", " でつなぐ(collect + joining)
        return null;
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
