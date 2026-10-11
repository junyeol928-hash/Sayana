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
// TODO: @RestControllerAdvice を付け、ResponseEntityExceptionHandler を継承する
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // TODO: BusinessRuleException → 409・title「業務ルール違反」・detail に例外メッセージ
    public ProblemDetail onRule(BusinessRuleException e) {
        return null;
    }

    // TODO: RequestNotFoundException → 404・title「申請が見つかりません」・detail に例外メッセージ
    public ProblemDetail onNotFound(RequestNotFoundException e) {
        return null;
    }

    // TODO: それ以外の例外 → 500・title「システムエラー」・detail「時間をおいて再度お試しください」。
    //       例外メッセージは返さない。errorId(UUID の先頭8文字)を作り、ログ(log.error)と本文の両方に入れる
    public ProblemDetail onUnexpected(Exception e) {
        return null;
    }
}
