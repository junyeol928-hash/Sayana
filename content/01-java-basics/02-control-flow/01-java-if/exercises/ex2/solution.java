public class ShippingFee {

    public static int calc(int amount, String rank) {
        if (amount <= 0) {
            return -1;
        }
        if ("GOLD".equals(rank)) {
            return 0;
        }
        if ("SILVER".equals(rank)) {
            return amount >= 3000 ? 0 : 300;
        }
        return amount >= 5000 ? 0 : 500;
    }

    public static void main(String[] args) {
        System.out.println(calc(100, "GOLD"));    // 0
        System.out.println(calc(2999, "SILVER")); // 300
        System.out.println(calc(3000, "SILVER")); // 0
        System.out.println(calc(1000, null));     // 500
        System.out.println(calc(0, "GOLD"));      // -1
    }
}
