import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// ===== controller =====
@RestController
@RequestMapping("/api/keys")
public class KeyIssueController {
    private final KeyIssueService keyIssueService;

    public KeyIssueController(KeyIssueService keyIssueService) {
        this.keyIssueService = keyIssueService;
    }

    @PostMapping
    public ResponseEntity<KeyIssueResponse> issue(@Valid @RequestBody KeyIssueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(keyIssueService.issue(request));
    }
}

// ===== dto(リクエストの形と制約) =====
record KeyIssueRequest(
        @NotBlank String cardId,
        @NotNull @Min(1) @Max(255) Integer keyVersion) {
}

record KeyIssueResponse(String keyId, String cardId, int keyVersion) {
}

// ===== service(直さなくてよい) =====
class KeyIssueService {
    private final List<KeyIssueResponse> issued = new ArrayList<>();

    KeyIssueResponse issue(KeyIssueRequest request) {
        int version = request.keyVersion();   // ← 本番ログの「at ...KeyIssueService.issue(KeyIssueService.java:31)」はこの行
        KeyIssueResponse res = new KeyIssueResponse(
                String.format("K-%04d", issued.size() + 1), request.cardId(), version);
        issued.add(res);
        return res;
    }

    int issuedCount() {
        return issued.size();
    }
}
