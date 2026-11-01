import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// ===== controller =====
@RestController
@RequestMapping("/api/keys")
class KeyIssueController {
    private final KeyIssueService keyIssueService;

    KeyIssueController(KeyIssueService keyIssueService) {
        this.keyIssueService = keyIssueService;
    }

    @PostMapping
    ResponseEntity<KeyIssueResponse> issue(@Valid @RequestBody KeyIssueRequest request) {
        KeyIssueResponse response = keyIssueService.issue(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

// ===== dto =====
record KeyIssueRequest(@NotBlank String cardId, @NotNull @Min(1) @Max(255) Integer keyVersion) {
}

record KeyIssueResponse(String keyId, String status) {
}

record ErrorResponse(String code, String message) {
}

// ===== service(ここを直す) =====
public class KeyIssueService {
    private final KeyRepository keyRepository;

    public KeyIssueService(KeyRepository keyRepository) {
        this.keyRepository = keyRepository;
    }

    public KeyIssueResponse issue(KeyIssueRequest request) {           // 1. リクエストを受け取る
        boolean duplicated = keyRepository
                .existsByCardIdAndStatus(request.cardId(), "ACTIVE"); // 2. 重複確認
        if (duplicated) {                                              // 3. 重複していたらエラー
            throw new KeyAlreadyExistsException(request.cardId());
        }
        KeyEntity saved = keyRepository.save(                         // 4・5. 採番して保存
                new KeyEntity(null, request.cardId(), request.keyVersion(), "ACTIVE"));
        return new KeyIssueResponse(saved.keyId(), saved.status());    // 6. 発行結果を返す
    }
}

// ===== repository(DB の代わりにメモリで持つ。直さなくてよい) =====
interface KeyRepository {
    boolean existsByCardIdAndStatus(String cardId, String status);

    KeyEntity save(KeyEntity entity);

    long count();
}

class InMemoryKeyRepository implements KeyRepository {
    private final List<KeyEntity> rows = new ArrayList<>();

    @Override
    public boolean existsByCardIdAndStatus(String cardId, String status) {
        return rows.stream().anyMatch(r -> r.cardId().equals(cardId) && r.status().equals(status));
    }

    @Override
    public KeyEntity save(KeyEntity entity) {
        String keyId = entity.keyId() != null ? entity.keyId() : String.format("K-%04d", rows.size() + 1);
        KeyEntity saved = new KeyEntity(keyId, entity.cardId(), entity.keyVersion(), entity.status());
        rows.add(saved);
        return saved;
    }

    @Override
    public long count() {
        return rows.size();
    }
}

// ===== entity =====
record KeyEntity(String keyId, String cardId, Integer keyVersion, String status) {
}

// ===== exception(直さなくてよい) =====
class KeyAlreadyExistsException extends RuntimeException {
    KeyAlreadyExistsException(String cardId) {
        super("カード " + cardId + " には有効な鍵がすでにあります");
    }
}

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(KeyAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> handleDuplicate(KeyAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("E409", e.getMessage()));
    }
}
