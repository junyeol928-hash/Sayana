public class Product {
    String name;
    int price;
    int quantity;

    Product(String name, int price, int quantity) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    int subtotal() {
        return price * quantity;
    }

    void add(int n) {
        quantity += n;
    }

    @Override
    public String toString() {
        return name + " " + price + "円 x " + quantity
                + "個 = " + subtotal() + "円";
    }

    public static void main(String[] args) {
        Product p = new Product("ペン", 120, 3);
        System.out.println(p.subtotal()); // 360
        p.add(2);
        System.out.println(p);            // ペン 120円 x 5個 = 600円
    }
}
