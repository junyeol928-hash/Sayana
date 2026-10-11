import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ContractDates {

    public static LocalDate endDate(LocalDate start, int months) {
        if (months < 1) {
            throw new IllegalArgumentException("契約月数は1以上です: " + months);
        }
        return start.plusMonths(months).minusDays(1);
    }

    public static LocalDate noticeDate(LocalDate end) {
        return end.minusMonths(1);
    }

    public static long daysLeft(LocalDate today, LocalDate end) {
        return ChronoUnit.DAYS.between(today, end);
    }

    public static void main(String[] args) {
        LocalDate end = endDate(LocalDate.of(2024, 4, 1), 12);
        System.out.println(end);                                     // 2025-03-31
        System.out.println(noticeDate(end));                         // 2025-02-28
        System.out.println(daysLeft(LocalDate.of(2025, 3, 1), end)); // 30
    }
}
