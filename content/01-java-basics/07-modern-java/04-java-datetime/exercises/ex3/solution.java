import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

public class BusinessDays {

    public static boolean isBusinessDay(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek w = date.getDayOfWeek();
        if (w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays.contains(date);
    }

    public static int count(LocalDate from, LocalDate to, Set<LocalDate> holidays) {
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (isBusinessDay(d, holidays)) {
                n++;
            }
        }
        return n; // from が to より後ならループに入らず 0
    }

    public static LocalDate addBusinessDays(LocalDate start, int n, Set<LocalDate> holidays) {
        if (n < 0) {
            throw new IllegalArgumentException("営業日数がマイナスです: " + n);
        }
        LocalDate d = start;
        int counted = 0;
        while (counted < n) {
            d = d.plusDays(1);
            if (isBusinessDay(d, holidays)) {
                counted++;
            }
        }
        return d;
    }

    public static void main(String[] args) {
        Set<LocalDate> gw = Set.of(LocalDate.of(2024, 4, 29),
                LocalDate.of(2024, 5, 3), LocalDate.of(2024, 5, 6));
        System.out.println(count(LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 9), Set.of())); // 5
        System.out.println(count(LocalDate.of(2024, 4, 29), LocalDate.of(2024, 5, 6), gw));      // 3
        System.out.println(addBusinessDays(LocalDate.of(2024, 5, 2), 1, gw));                    // 2024-05-07
    }
}
