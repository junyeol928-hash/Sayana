public class SumCalculator {

    static long sumTo(int n) {
        long sum = 0;
        for (int i = 1; i <= n; i++) {   // n が 0 以下ならループに入らず 0
            sum += i;
        }
        return sum;
    }

    static long sumOf(int[] values) {
        long sum = 0;
        for (int v : values) {
            sum += v;
        }
        return sum;
    }

    public static void main(String[] args) {
        System.out.println(sumTo(10));                     // 55
        System.out.println(sumOf(new int[]{3, 4, 5}));     // 12
        System.out.println(sumTo(1_000_000));              // 500000500000
    }
}
