import java.security.Principal;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ===== domain(用意済み) =====
enum RequestType { EXPENSE, LEAVE, PURCHASE }

enum RequestStatus { DRAFT, SUBMITTED, APPROVED, RETURNED, WITHDRAWN }

record Employee(Long id, String name, String email) {
}

/** STS では JPA のエンティティ(class + getter)。採点用に record で簡略化している */
record ApprovalRequest(Long id, String title, RequestType type, Integer amount,
        String reason, RequestStatus status, Employee applicant, Employee approver,
        String comment, LocalDateTime createdAt, LocalDateTime submittedAt,
        LocalDateTime decidedAt) {
}

/** 業務ルール違反(業務ルール7) */
class BusinessRuleException extends RuntimeException {
    BusinessRuleException(String message) {
        super(message);
    }
}

/** 申請が存在しない(業務ルール違反ではない) */
class RequestNotFoundException extends RuntimeException {
    RequestNotFoundException(Long id) {
        super("申請(ID=" + id + ")が見つかりません");
    }
}

// ===== service(用意済み。テスト側でメモリ実装を作って渡す) =====
interface RequestService {
    /** なければ RequestNotFoundException */
    ApprovalRequest get(Long id);

    /** loginId の人が承認する。なければ RequestNotFoundException、ルール違反は BusinessRuleException */
    ApprovalRequest approve(Long id, String loginId);
}

// ===== web.api の DTO(用意済み) =====
record RequestDetail(Long id, String title, RequestType type, Integer amount,
        String reason, RequestStatus status, String applicantName, String approverName,
        String comment, LocalDateTime createdAt, LocalDateTime submittedAt,
        LocalDateTime decidedAt) {

    static RequestDetail from(ApprovalRequest r) {
        String approverName = r.approver() == null ? null : r.approver().name();
        return new RequestDetail(r.id(), r.title(), r.type(), r.amount(), r.reason(),
                r.status(), r.applicant().name(), approverName, r.comment(),
                r.createdAt(), r.submittedAt(), r.decidedAt());
    }
}

// ===== web.api の Controller =====
@RestController
@RequestMapping("/api/requests")
public class RequestApiController {

    private final RequestService service;

    public RequestApiController(RequestService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public RequestDetail detail(@PathVariable Long id) {
        return RequestDetail.from(service.get(id));
    }

    // TODO: POST /api/requests/{id}/approve に対応させる。
    //       principal.getName()(ログインID)を service.approve に渡し、結果を RequestDetail にして返す
    public RequestDetail approve(Long id, Principal principal) {
        return null;
    }

    // TODO: @ExceptionHandler を付け、409 Conflict・title「業務ルール違反」・detail に例外メッセージの ProblemDetail を返す
    public ProblemDetail onRule(BusinessRuleException e) {
        return null;
    }

    // TODO: @ExceptionHandler を付け、404 Not Found・title「申請が見つかりません」・detail に例外メッセージの ProblemDetail を返す
    public ProblemDetail onNotFound(RequestNotFoundException e) {
        return null;
    }
}
