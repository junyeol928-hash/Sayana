public class SumCalculator {

    static long sumTo(int n) {
        // TODO: for 文で書き直す(再帰だと n が大きいと StackOverflowError)
        if (n <= 0) {
            return 0;
        }
        return n + sumTo(n - 1);
    }

    static long sumOf(int[] values) {
        // TODO: for 文で書き直す
        return sumFrom(values, 0);
    }

    private static long sumFrom(int[] values, int index) {
        if (index >= values.length) {
            return 0;
        }
        return values[index] + sumFrom(values, index + 1);
    }

    public static void main(String[] args) {
        System.out.println(sumTo(10));                     // 55
        System.out.println(sumOf(new int[]{3, 4, 5}));     // 12
        System.out.println(sumTo(1_000_000));              // 500000500000
    }
}
