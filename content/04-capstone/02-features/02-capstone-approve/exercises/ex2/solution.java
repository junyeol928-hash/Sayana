import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 承認・差戻しの業務ルール(1〜3・6)を守るサービス。STS では @Service @Transactional */
public class ApprovalService {

    private final ApprovalRequestRepository requests;
    private final EmployeeRepository employees;
    private final ApprovalHistoryRepository histories;

    public ApprovalService(ApprovalRequestRepository requests,
                           EmployeeRepository employees,
                           ApprovalHistoryRepository histories) {
        this.requests = requests;
        this.employees = employees;
        this.histories = histories;
    }

    /** 申請・再申請(ルール1:申請者本人だけ) */
    public ApprovalRequest submit(Long requestId, Long actorId) {
        ApprovalRequest r = findRequest(requestId);
        Employee actor = findEmployee(actorId);
        requireApplicant(r, actor);
        LocalDateTime now = LocalDateTime.now();
        r.submit(now);
        record(r, actor, Action.SUBMIT, null, now);
        return r;
    }

    /** 承認(ルール2・3) */
    public ApprovalRequest approve(Long requestId, Long actorId, String comment) {
        ApprovalRequest r = findRequest(requestId);
        Employee actor = findEmployee(actorId);
        requireApprover(actor);
        if (r.getApplicant().id().equals(actor.id())) {
            throw new BusinessRuleException("自分の申請は承認できません");
        }
        if (ApprovalFlow.requiresAdmin(r.getAmount()) && actor.role() != Role.ADMIN) {
            throw new BusinessRuleException("100,000円以上の申請は管理者しか承認できません");
        }
        LocalDateTime now = LocalDateTime.now();
        r.approve(actor, comment, now);
        record(r, actor, Action.APPROVE, comment, now);
        return r;
    }

    /** 差戻し(ルール2。コメント必須のルール4はエンティティが守る) */
    public ApprovalRequest returnBack(Long requestId, Long actorId, String comment) {
        ApprovalRequest r = findRequest(requestId);
        Employee actor = findEmployee(actorId);
        requireApprover(actor);
        LocalDateTime now = LocalDateTime.now();
        r.returnBack(actor, comment, now);
        record(r, actor, Action.RETURN, comment, now);
        return r;
    }

    /** 取下げ(ルール1) */
    public ApprovalRequest withdraw(Long requestId, Long actorId) {
        ApprovalRequest r = findRequest(requestId);
        Employee actor = findEmployee(actorId);
        requireApplicant(r, actor);
        LocalDateTime now = LocalDateTime.now();
        r.withdraw();
        record(r, actor, Action.WITHDRAW, null, now);
        return r;
    }

    private ApprovalRequest findRequest(Long id) {
        return requests.findById(id)
                .orElseThrow(() -> new BusinessRuleException("申請が見つかりません"));
    }

    private Employee findEmployee(Long id) {
        return employees.findById(id)
                .orElseThrow(() -> new BusinessRuleException("社員が見つかりません"));
    }

    private void requireApplicant(ApprovalRequest r, Employee actor) {
        if (!r.getApplicant().id().equals(actor.id())) {
            throw new BusinessRuleException("申請者本人しか操作できません");
        }
    }

    private void requireApprover(Employee actor) {
        if (actor.role() != Role.APPROVER && actor.role() != Role.ADMIN) {
            throw new BusinessRuleException("承認者・管理者しか操作できません");
        }
    }

    /** ルール6:状態を変えたら必ず履歴を1件(STS では同じトランザクションで保存される) */
    private void record(ApprovalRequest r, Employee actor, Action action, String comment, LocalDateTime now) {
        histories.save(new ApprovalHistory(r.getId(), actor.id(), action, comment, now));
    }
}

/** 申請(前の問題の答え。完成済み) */
class ApprovalRequest {
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
        status = ApprovalFlow.next(status, Action.SUBMIT);
        submittedAt = now;
    }

    /** 承認する。comment は任意 */
    public void approve(Employee approver, String comment, LocalDateTime now) {
        status = ApprovalFlow.next(status, Action.APPROVE);
        decide(approver, comment, now);
    }

    /** 差し戻す。コメント必須(業務ルール4) */
    public void returnBack(Employee approver, String comment, LocalDateTime now) {
        if (comment == null || comment.isBlank()) {
            throw new BusinessRuleException("差戻しにはコメントが必要です");
        }
        status = ApprovalFlow.next(status, Action.RETURN);
        decide(approver, comment, now);
    }

    /** 取り下げる */
    public void withdraw() {
        status = ApprovalFlow.next(status, Action.WITHDRAW);
    }

    private void decide(Employee approver, String comment, LocalDateTime now) {
        this.approver = approver;
        this.comment = comment;
        this.decidedAt = now;
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

/** 承認履歴(エンティティの縮小版。STS では request・actor は ApprovalRequest・Employee への参照。完成済み) */
record ApprovalHistory(Long requestId, Long actorId, Action action, String comment, LocalDateTime at) {}

/** 申請の Repository(STS では JpaRepository を継承するインターフェース。完成済み) */
interface ApprovalRequestRepository {
    Optional<ApprovalRequest> findById(Long id);

    ApprovalRequest save(ApprovalRequest request);
}

/** 社員の Repository(完成済み) */
interface EmployeeRepository {
    Optional<Employee> findById(Long id);
}

/** 履歴の Repository(完成済み) */
interface ApprovalHistoryRepository {
    ApprovalHistory save(ApprovalHistory history);

    List<ApprovalHistory> findByRequestId(Long requestId);
}

/** メモリ上の実装(テストで差し込む。完成済み) */
class InMemoryApprovalRequestRepository implements ApprovalRequestRepository {
    private final Map<Long, ApprovalRequest> store = new HashMap<>();

    @Override
    public Optional<ApprovalRequest> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public ApprovalRequest save(ApprovalRequest request) {
        store.put(request.getId(), request);
        return request;
    }
}

class InMemoryEmployeeRepository implements EmployeeRepository {
    private final Map<Long, Employee> store = new HashMap<>();

    void add(Employee e) {
        store.put(e.id(), e);
    }

    @Override
    public Optional<Employee> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }
}

class InMemoryApprovalHistoryRepository implements ApprovalHistoryRepository {
    private final List<ApprovalHistory> store = new ArrayList<>();

    @Override
    public ApprovalHistory save(ApprovalHistory history) {
        store.add(history);
        return history;
    }

    @Override
    public List<ApprovalHistory> findByRequestId(Long requestId) {
        return store.stream().filter(h -> h.requestId().equals(requestId)).toList();
    }
}
