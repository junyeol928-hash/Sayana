import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ApplicationFlow {

    public static Map<Status, Integer> countByStatus(List<Status> statuses) {
        Map<Status, Integer> counts = new EnumMap<>(Status.class);
        // TODO: Status.values() で全ステータスを 0 件で入れておく
        // TODO: statuses を数える(counts.merge(s, 1, Integer::sum) が便利)
        return counts;
    }

    public static void main(String[] args) {
        System.out.println(Status.DRAFT.canMoveTo(Status.SUBMITTED));    // true
        System.out.println(Status.DRAFT.canMoveTo(Status.APPROVED));     // false
        System.out.println(Status.APPROVED.canMoveTo(Status.DRAFT));     // false
        System.out.println(countByStatus(List.of(Status.DRAFT, Status.SUBMITTED, Status.DRAFT)));
        // {DRAFT=2, SUBMITTED=1, APPROVED=0, REJECTED=0}
    }
}

enum Status {
    DRAFT, SUBMITTED, APPROVED, REJECTED;

    boolean canMoveTo(Status next) {
        // TODO: next が null なら false
        // TODO: switch (this) の switch 式で、遷移できる先なら true を返す
        //       例: case DRAFT -> next == SUBMITTED;
        return false; // ← ダミー。書き換えてください
    }
}
