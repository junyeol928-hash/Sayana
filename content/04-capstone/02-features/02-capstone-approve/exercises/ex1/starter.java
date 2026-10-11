import java.time.LocalDateTime;

/** 申請(エンティティの縮小版)。状態の整合性(業務ルール4・5)は自分で守る */
public class ApprovalRequest {
    private final Long id;
    private final String title;
    private final Integer amount;
    private final Employee applicant;
    private RequestStatus status = RequestStatus.DRAFT;
    private Employee approver;
    private String comment;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;

    public ApprovalRequest(Long id, String title, Integer amount, Employee applicant) {
        this.id = id;
        this.title = title;
        this.amount = amount;
        this.applicant = applicant;
    }

    /** 申請する(差戻し後なら再申請) */
    public void submit(LocalDateTime now) {
        // TODO: ApprovalFlow.next(status, Action.SUBMIT) で次の状態にし、submittedAt に now を入れる
    }

    /** 承認する。comment は任意 */
    public void approve(Employee approver, String comment, LocalDateTime now) {
        // TODO: 状態を APPROVE 後の状態にし、approver・comment・decidedAt を記録する
    }

    /** 差し戻す。コメント必須(業務ルール4) */
    public void returnBack(Employee approver, String comment, LocalDateTime now) {
        // TODO: comment が null か空白だけなら BusinessRuleException("差戻しにはコメントが必要です")
        // TODO: 状態を RETURN 後の状態にし、approver・comment・decidedAt を記録する
    }

    /** 取り下げる */
    public void withdraw() {
        // TODO: 状態を WITHDRAW 後の状態にする
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Integer getAmount() { return amount; }
    public Employee getApplicant() { return applicant; }
    public RequestStatus getStatus() { return status; }
    public Employee getApprover() { return approver; }
    public String getComment() { return comment; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
}

/** 業務ルール違反(業務ルール7。完成済み) */
class BusinessRuleException extends RuntimeException {
    BusinessRuleException(String message) {
        super(message);
    }
}

/** 社員の役割(完成済み) */
enum Role { APPLICANT, APPROVER, ADMIN }

/** 社員(エンティティの縮小版。完成済み) */
record Employee(Long id, String name, Role role) {}

/** 申請の状態(capstone-requirements の答え。完成済み) */
enum RequestStatus {
    DRAFT("下書き"), SUBMITTED("申請中"), APPROVED("承認済"), RETURNED("差戻し"), WITHDRAWN("取下げ");

    private final String label;

    RequestStatus(String label) { this.label = label; }

    String label() { return label; }

    boolean canTransitionTo(RequestStatus next) {
        return switch (this) {
            case DRAFT -> next == SUBMITTED || next == WITHDRAWN;
            case SUBMITTED -> next == APPROVED || next == RETURNED;
            case RETURNED -> next == SUBMITTED || next == WITHDRAWN;
            case APPROVED, WITHDRAWN -> false;
        };
    }

    boolean isFinal() { return this == APPROVED || this == WITHDRAWN; }
}

/** 操作。行き先の状態を持つ(完成済み) */
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

    String label() { return label; }

    RequestStatus target() { return target; }
}

/** 状態遷移と高額判定(capstone-requirements の答え。完成済み) */
class ApprovalFlow {
    static final int ADMIN_LIMIT = 100_000;

    /** current に action をした後の状態。できなければ BusinessRuleException(業務ルール5) */
    static RequestStatus next(RequestStatus current, Action action) {
        if (current.isFinal()) {
            throw new BusinessRuleException(current.label() + "の申請は変更できません");
        }
        RequestStatus to = action.target();
        if (!current.canTransitionTo(to)) {
            throw new BusinessRuleException(current.label() + "の申請は「" + action.label() + "」できません");
        }
        return to;
    }

    /** この金額の承認に ADMIN が必要か(業務ルール3)。休暇は null */
    static boolean requiresAdmin(Integer amount) {
        return amount != null && amount >= ADMIN_LIMIT;
    }
}
