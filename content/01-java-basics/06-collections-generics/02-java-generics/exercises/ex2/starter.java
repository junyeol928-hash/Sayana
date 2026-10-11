import java.util.ArrayList;
import java.util.List;

public class ExpenseReport {

    public static double total(List<? extends Number> amounts) {
        // TODO: amounts が null なら 0.0 を返す

        // TODO: 拡張 for で Number として1件ずつ取り出し、null でなければ doubleValue() を足す

        return -1; // ← ダミー。書き換えてください
    }

    public static int collectLarge(List<? extends Number> src, double threshold,
                                   List<? super Double> dest) {
        // TODO: src が null なら 0 を返す

        // TODO: null でない要素のうち、doubleValue() が threshold 以上のものを dest に add し、件数を数える

        return -1; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        System.out.println(total(List.of(1200, 800, 3000)));     // 5000.0
        System.out.println(total(List.of(10.5, 20.25)));         // 30.75

        List<Number> audit = new ArrayList<>();
        int n = collectLarge(List.of(3000, 52000, 50000, 800), 50000, audit);
        System.out.println(n + "件: " + audit);                  // 2件: [52000.0, 50000.0]
    }
}
