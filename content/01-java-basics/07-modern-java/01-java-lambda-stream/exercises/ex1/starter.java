import java.util.List;

class Order {
    private final String customer;
    private final String status;
    private final int amount;

    Order(String customer, String status, int amount) {
        this.customer = customer;
        this.status = status;
        this.amount = amount;
    }

    String getCustomer() { return customer; }
    String getStatus() { return status; }
    int getAmount() { return amount; }
}

public class OrderTotal {

    public static int totalByStatus(List<Order> orders, String status) {
        // TODO: status が一致する注文だけに絞り(filter)、
        //       金額を取り出して(mapToInt)合計する(sum)
        return 0;
    }

    public static long countAtLeast(List<Order> orders, int min) {
        // TODO: 金額が min 以上の注文に絞って、件数を数える(count)
        return 0;
    }

    public static void main(String[] args) {
        List<Order> orders = List.of(
                new Order("佐藤", "PAID", 3000),
                new Order("鈴木", "CANCELLED", 5000),
                new Order("田中", "PAID", 4500),
                new Order("伊藤", "PENDING", 1200));
        System.out.println(totalByStatus(orders, "PAID")); // 7500
        System.out.println(countAtLeast(orders, 4500));    // 2
    }
}
