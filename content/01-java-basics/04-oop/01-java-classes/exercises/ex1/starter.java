public class Product {
    String name;
    int price;
    int quantity;

    Product(String name, int price, int quantity) {
        // TODO: 引数の値を3つのフィールドに入れる(this. を忘れずに)
    }

    int subtotal() {
        // TODO: 単価 × 数量 を返す
        return 0; // ← ダミー。書き換えてください
    }

    void add(int n) {
        // TODO: 数量を n 個増やす
    }

    @Override
    public String toString() {
        // TODO: "ペン 120円 x 3個 = 360円" の形式で返す
        return ""; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        Product p = new Product("ペン", 120, 3);
        System.out.println(p.subtotal()); // 360
        p.add(2);
        System.out.println(p);            // ペン 120円 x 5個 = 600円
    }
}
