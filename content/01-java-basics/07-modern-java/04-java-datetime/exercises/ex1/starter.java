import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ContractDates {

    public static LocalDate endDate(LocalDate start, int months) {
        // TODO: months が 1 未満なら IllegalArgumentException を投げる
        // TODO: start の months か月後の「前日」を返す(plusMonths と minusDays)
        return start; // ← ダミー。書き換えてください
    }

    public static LocalDate noticeDate(LocalDate end) {
        // TODO: end の1か月前の日を返す
        return end; // ← ダミー。書き換えてください
    }

    public static long daysLeft(LocalDate today, LocalDate end) {
        // TODO: ChronoUnit.DAYS.between で today から end までの日数を返す
        return 0; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        LocalDate end = endDate(LocalDate.of(2024, 4, 1), 12);
        System.out.println(end);                                     // 2025-03-31
        System.out.println(noticeDate(end));                         // 2025-02-28
        System.out.println(daysLeft(LocalDate.of(2025, 3, 1), end)); // 30
    }
}
