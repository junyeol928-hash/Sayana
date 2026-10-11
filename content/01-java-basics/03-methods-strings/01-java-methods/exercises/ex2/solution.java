public class StockUpdater {

    public static boolean ship(int[] stock, int index, int qty) {
        if (stock == null) {
            return false;
        }
        if (index < 0 || index >= stock.length) {
            return false;
        }
        if (qty <= 0) {
            return false;
        }
        if (stock[index] < qty) {
            return false;
        }
        stock[index] -= qty;
        return true;
    }

    public static int[] restocked(int[] stock, int qty) {
        if (stock == null) {
            return new int[0];
        }
        int[] result = new int[stock.length];
        for (int i = 0; i < stock.length; i++) {
            result[i] = stock[i] + qty;
        }
        return result;
    }

    public static void main(String[] args) {
        int[] stock = {10, 0, 5};
        System.out.println(ship(stock, 0, 3));                       // true
        System.out.println(java.util.Arrays.toString(stock));       // [7, 0, 5]
        System.out.println(ship(stock, 1, 1));                       // false
        int[] next = restocked(stock, 2);
        System.out.println(java.util.Arrays.toString(next));        // [9, 2, 7]
        System.out.println(java.util.Arrays.toString(stock));       // [7, 0, 5]
    }
}
