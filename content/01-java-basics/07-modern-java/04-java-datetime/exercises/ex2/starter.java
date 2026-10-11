import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class WorkTime {

    public static Duration actual(Shift s) {
        // TODO: end が start より後でなければ IllegalArgumentException
        // TODO: 休憩がマイナスなら IllegalArgumentException
        // TODO: Duration.between(start, end) から休憩分を引く
        // TODO: 結果がマイナス(isNegative)なら IllegalArgumentException
        return Duration.ZERO; // ← ダミー。書き換えてください
    }

    public static Duration total(List<Shift> shifts) {
        Duration sum = Duration.ZERO;
        // TODO: 各シフトの actual を sum に足す(戻り値を受け取ること!)
        return sum;
    }

    public static String format(Duration d) {
        // TODO: "7時間30分" の形式にする(toHours と toMinutesPart)
        return ""; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        Shift day = new Shift(LocalDateTime.of(2024, 4, 1, 9, 0),
                LocalDateTime.of(2024, 4, 1, 18, 0), 60);
        Shift night = new Shift(LocalDateTime.of(2024, 4, 1, 22, 0),
                LocalDateTime.of(2024, 4, 2, 6, 0), 60);
        System.out.println(actual(day));                           // PT8H
        System.out.println(actual(night));                         // PT7H
        System.out.println(format(total(List.of(day, night))));    // 15時間0分
    }
}

record Shift(LocalDateTime start, LocalDateTime end, int breakMinutes) {
}
