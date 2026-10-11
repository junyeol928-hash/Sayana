import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

// ===== service(用意済み。テスト側でメモリ実装を作って渡す) =====
interface RequestService {
    /** status が null なら全件。どちらも ID の昇順で返す */
    List<ApprovalRequest> findByStatus(RequestStatus status);

    Optional<ApprovalRequest> findById(Long id);
}

// ===== web.api の DTO =====
record RequestSummary(Long id, String title, RequestType type, Integer amount,
        RequestStatus status, String applicantName, LocalDateTime submittedAt) {

    static RequestSummary from(ApprovalRequest r) {
        // TODO: r から一覧用の項目だけを詰め替える(applicantName は申請者の名前)
        return null;
    }
}

record RequestDetail(Long id, String title, RequestType type, Integer amount,
        String reason, RequestStatus status, String applicantName, String approverName,
        String comment, LocalDateTime createdAt, LocalDateTime submittedAt,
        LocalDateTime decidedAt) {

    static RequestDetail from(ApprovalRequest r) {
        // TODO: r から詳細用の項目を詰め替える(approver が null なら approverName も null)
        return null;
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

    // TODO: GET /api/requests?status=SUBMITTED に対応させる(status は省略可)
    public List<RequestSummary> list(RequestStatus status) {
        return List.of();
    }

    // TODO: GET /api/requests/{id} に対応させる。見つからなければ 404
    public ResponseEntity<RequestDetail> detail(Long id) {
        return ResponseEntity.notFound().build();
    }
}
