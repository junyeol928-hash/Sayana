import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

public class BusinessDays {

    public static boolean isBusinessDay(LocalDate date, Set<LocalDate> holidays) {
        // TODO: 土曜・日曜なら false(date.getDayOfWeek() と DayOfWeek.SATURDAY などを比べる)
        // TODO: holidays に含まれていれば false
        return false; // ← ダミー。書き換えてください
    }

    public static int count(LocalDate from, LocalDate to, Set<LocalDate> holidays) {
        // TODO: from から to まで(両端を含む)1日ずつ進めて、営業日を数える
        //       from が to より後なら 0
        return -1; // ← ダミー。書き換えてください
    }

    public static LocalDate addBusinessDays(LocalDate start, int n, Set<LocalDate> holidays) {
        // TODO: n がマイナスなら IllegalArgumentException
        // TODO: start の翌日から進めて、営業日を n 個数えた日を返す(n が 0 なら start)
        return null; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        Set<LocalDate> gw = Set.of(LocalDate.of(2024, 4, 29),
                LocalDate.of(2024, 5, 3), LocalDate.of(2024, 5, 6));
        System.out.println(count(LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 9), Set.of())); // 5
        System.out.println(count(LocalDate.of(2024, 4, 29), LocalDate.of(2024, 5, 6), gw));      // 3
        System.out.println(addBusinessDays(LocalDate.of(2024, 5, 2), 1, gw));                    // 2024-05-07
    }
}
