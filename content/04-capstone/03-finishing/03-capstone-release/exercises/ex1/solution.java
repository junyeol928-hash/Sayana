import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// ===== domain(用意済み) =====
enum RequestStatus { DRAFT, SUBMITTED, APPROVED, RETURNED, WITHDRAWN }

/** 業務ルール違反(業務ルール7) */
class BusinessRuleException extends RuntimeException {
    BusinessRuleException(String message) {
        super(message);
    }
}

/** 申請が存在しない */
class RequestNotFoundException extends RuntimeException {
    RequestNotFoundException(Long id) {
        super("申請(ID=" + id + ")が見つかりません");
    }
}

// ===== service(用意済み。テスト側でメモリ実装を作って渡す) =====
interface RequestService {
    List<RequestDetail> findByStatus(RequestStatus status);

    /** なければ RequestNotFoundException */
    RequestDetail get(Long id);

    /** ルール違反は BusinessRuleException */
    RequestDetail approve(Long id, String loginId);
}

// ===== web.api(用意済み。Controller の中にはもう @ExceptionHandler を書かない) =====
record RequestDetail(Long id, String title, RequestStatus status) {
}

@RestController
@RequestMapping("/api/requests")
class RequestApiController {
    private final RequestService service;

    RequestApiController(RequestService service) {
        this.service = service;
    }

    @GetMapping
    List<RequestDetail> list(@RequestParam(required = false) RequestStatus status) {
        return service.findByStatus(status);
    }

    @GetMapping("/{id}")
    RequestDetail detail(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve")
    RequestDetail approve(@PathVariable Long id, Principal principal) {
        return service.approve(id, principal.getName());
    }
}

// ===== web.api の共通エラーハンドラ =====
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail onRule(BusinessRuleException e) {
        return problem(HttpStatus.CONFLICT, "業務ルール違反", e.getMessage());
    }

    @ExceptionHandler(RequestNotFoundException.class)
    public ProblemDetail onNotFound(RequestNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "申請が見つかりません", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception e) {
        String errorId = UUID.randomUUID().toString().substring(0, 8);
        log.error("想定外のエラー errorId={}", errorId, e);
        ProblemDetail pd = problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "システムエラー", "時間をおいて再度お試しください");
        pd.setProperty("errorId", errorId);
        return pd;
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
