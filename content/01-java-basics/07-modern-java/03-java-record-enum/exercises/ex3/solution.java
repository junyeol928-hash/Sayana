import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ApplicationFlow {

    public static Map<Status, Integer> countByStatus(List<Status> statuses) {
        Map<Status, Integer> counts = new EnumMap<>(Status.class);
        for (Status s : Status.values()) {
            counts.put(s, 0);
        }
        for (Status s : statuses) {
            counts.merge(s, 1, Integer::sum);
        }
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
        if (next == null) {
            return false;
        }
        return switch (this) {
            case DRAFT -> next == SUBMITTED;
            case SUBMITTED -> next == APPROVED || next == REJECTED || next == DRAFT;
            case REJECTED -> next == DRAFT;
            case APPROVED -> false; // 最終状態
        };
    }
}
