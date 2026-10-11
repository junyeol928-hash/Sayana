public class InvoiceCalc {

    public static int subtotal(int unitPrice, int qty) {
        return unitPrice * qty;
    }

    public static int tax(int amount) {
        return amount * 10 / 100;
    }

    public static int total(int unitPrice, int qty) {
        return total(unitPrice, qty, 0);
    }

    public static int total(int unitPrice, int qty, int discount) {
        int amount = subtotal(unitPrice, qty) - discount;
        if (amount < 0) {
            amount = 0;
        }
        return amount + tax(amount);
    }

    public static void main(String[] args) {
        System.out.println(subtotal(980, 3));     // 2940
        System.out.println(tax(999));             // 99
        System.out.println(total(980, 3));        // 3234
        System.out.println(total(980, 3, 440));   // 2750
        System.out.println(total(100, 1, 500));   // 0
    }
}
