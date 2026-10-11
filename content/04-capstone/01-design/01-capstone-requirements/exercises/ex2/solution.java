// 業務ルール違反を表す例外(業務ルール7。完成済み)
class BusinessRuleException extends RuntimeException {
    BusinessRuleException(String message) {
        super(message);
    }
}

// 申請の状態(前の問題の答え。完成済み)
enum RequestStatus {
    DRAFT("下書き"),
    SUBMITTED("申請中"),
    APPROVED("承認済"),
    RETURNED("差戻し"),
    WITHDRAWN("取下げ");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    boolean canTransitionTo(RequestStatus next) {
        return switch (this) {
            case DRAFT -> next == SUBMITTED || next == WITHDRAWN;
            case SUBMITTED -> next == APPROVED || next == RETURNED;
            case RETURNED -> next == SUBMITTED || next == WITHDRAWN;
            case APPROVED, WITHDRAWN -> false;
        };
    }

    boolean isFinal() {
        return this == APPROVED || this == WITHDRAWN;
    }
}

// 画面のボタン = 操作。それぞれ行き先の状態を持つ(完成済み)
enum Action {
    SUBMIT("申請", RequestStatus.SUBMITTED),
    APPROVE("承認", RequestStatus.APPROVED),
    RETURN("差戻し", RequestStatus.RETURNED),
    WITHDRAW("取下げ", RequestStatus.WITHDRAWN);

    private final String label;
    private final RequestStatus target;

    Action(String label, RequestStatus target) {
        this.label = label;
        this.target = target;
    }

    String label() {
        return label;
    }

    RequestStatus target() {
        return target;
    }
}

public class ApprovalFlow {

    /** これ以上の金額は ADMIN しか承認できない(業務ルール3) */
    static final int ADMIN_LIMIT = 100_000;

    /** current の申請に action をした後の状態。できない操作なら BusinessRuleException */
    public static RequestStatus next(RequestStatus current, Action action) {
        if (current.isFinal()) {
            throw new BusinessRuleException(current.label() + "の申請は変更できません");
        }
        RequestStatus to = action.target();
        if (!current.canTransitionTo(to)) {
            throw new BusinessRuleException(
                    current.label() + "の申請は「" + action.label() + "」できません");
        }
        return to;
    }

    /** この金額の申請の承認に ADMIN が必要か。amount は休暇申請では null */
    public static boolean requiresAdmin(Integer amount) {
        return amount != null && amount >= ADMIN_LIMIT;
    }
}
