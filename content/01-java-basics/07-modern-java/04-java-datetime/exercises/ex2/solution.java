import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class WorkTime {

    public static Duration actual(Shift s) {
        if (!s.end().isAfter(s.start())) {
            throw new IllegalArgumentException("終了が開始より前です: " + s);
        }
        if (s.breakMinutes() < 0) {
            throw new IllegalArgumentException("休憩がマイナスです: " + s);
        }
        Duration d = Duration.between(s.start(), s.end()).minusMinutes(s.breakMinutes());
        if (d.isNegative()) {
            throw new IllegalArgumentException("休憩が勤務時間より長いです: " + s);
        }
        return d;
    }

    public static Duration total(List<Shift> shifts) {
        Duration sum = Duration.ZERO;
        for (Shift s : shifts) {
            sum = sum.plus(actual(s)); // 不変なので受け取り直す
        }
        return sum;
    }

    public static String format(Duration d) {
        return d.toHours() + "時間" + d.toMinutesPart() + "分";
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
