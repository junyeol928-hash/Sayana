import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// ===== 入力(用意済み) =====
record MemberRequest(
        @NotBlank(message = "氏名は必須です") String name,
        @NotBlank(message = "メールアドレスは必須です")
        @Email(message = "メールアドレスの形式が正しくありません") String email,
        @NotNull(message = "年齢は必須です")
        @Min(value = 18, message = "18歳以上の方のみ登録できます")
        @Max(value = 120, message = "年齢は120以下で入力してください") Integer age) {
}

record MemberResponse(long id, String name, String email, int age) {
}

@RestController
@RequestMapping("/api/members")
class MemberController {
    private final AtomicLong seq = new AtomicLong();

    @PostMapping
    ResponseEntity<MemberResponse> register(@Valid @RequestBody MemberRequest req) {
        long id = seq.incrementAndGet();
        return ResponseEntity.created(URI.create("/api/members/" + id))
                .body(new MemberResponse(id, req.name(), req.email(), req.age()));
    }
}

// ===== ここを完成させる =====
@RestControllerAdvice
public class ValidationErrorHandler {

    // TODO: @ExceptionHandler(MethodArgumentNotValidException.class) を付ける
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        // TODO: ex.getBindingResult().getFieldErrors() を回して、
        //       項目名(getField())→ メッセージ(getDefaultMessage())の Map を作る(putIfAbsent)

        // TODO: ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "入力内容を確認してください") で作り、
        //       setTitle("入力エラー")、setProperty("errors", map) して返す
        return null; // ← ダミー
    }
}
