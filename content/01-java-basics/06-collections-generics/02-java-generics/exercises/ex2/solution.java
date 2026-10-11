import java.util.ArrayList;
import java.util.List;

public class ExpenseReport {

    public static double total(List<? extends Number> amounts) {
        if (amounts == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (Number n : amounts) {
            if (n != null) {
                sum += n.doubleValue();
            }
        }
        return sum;
    }

    public static int collectLarge(List<? extends Number> src, double threshold,
                                   List<? super Double> dest) {
        if (src == null) {
            return 0;
        }
        int count = 0;
        for (Number n : src) {
            if (n != null && n.doubleValue() >= threshold) {
                dest.add(n.doubleValue());
                count++;
            }
        }
        return count;
    }

    public static void main(String[] args) {
        System.out.println(total(List.of(1200, 800, 3000)));     // 5000.0
        System.out.println(total(List.of(10.5, 20.25)));         // 30.75

        List<Number> audit = new ArrayList<>();
        int n = collectLarge(List.of(3000, 52000, 50000, 800), 50000, audit);
        System.out.println(n + "件: " + audit);                  // 2件: [52000.0, 50000.0]
    }
}
