import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/requests")
public class RequestController {

    /** ログインの代わり。capstone-security でログインユーザーに置き換える */
    static final Long CURRENT_EMPLOYEE_ID = 1L;

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    /** このクラスの全ハンドラの前に呼ばれ、Model に "types" を入れる */
    @ModelAttribute("types")
    public RequestType[] types() {
        return RequestType.values();
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("requests", requestService.findMine(CURRENT_EMPLOYEE_ID));
        return "requests/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("requestForm", new RequestForm());
        return "requests/new";
    }

    @PostMapping
    public String create(@Validated @ModelAttribute RequestForm form,
                         BindingResult result,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "requests/new";            // 入力値とエラーを持ったまま同じ画面へ
        }
        ApprovalRequest saved = requestService.createDraft(CURRENT_EMPLOYEE_ID, form.toEntity());
        ra.addFlashAttribute("message", "下書きを保存しました(No." + saved.getId() + ")");
        return "redirect:/requests";          // PRG
    }
}

/** 新規申請フォーム(前の問題の答え。完成済み) */
class RequestForm {

    @NotBlank(message = "件名を入力してください")
    @Size(max = 50, message = "件名は50文字以内で入力してください")
    private String title;

    @NotNull(message = "種類を選んでください")
    private RequestType type;

    @Min(value = 1, message = "金額は1円以上で入力してください")
    private Integer amount;

    @NotBlank(message = "理由を入力してください")
    private String reason;

    @AssertTrue(message = "経費精算・備品購入は金額が必須です")
    public boolean isAmountEntered() {
        if (type == null || !type.requiresAmount()) {
            return true;
        }
        return amount != null;
    }

    /** フォーム → エンティティへの詰め替え。休暇なら金額は捨てる */
    ApprovalRequest toEntity() {
        ApprovalRequest r = new ApprovalRequest();
        r.setTitle(title);
        r.setType(type);
        r.setAmount(type.requiresAmount() ? amount : null);
        r.setReason(reason);
        return r;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public RequestType getType() { return type; }
    public void setType(RequestType type) { this.type = type; }
    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}

/** 申請の種類(完成済み) */
enum RequestType {
    EXPENSE("経費精算"), LEAVE("休暇"), PURCHASE("備品購入");

    private final String label;

    RequestType(String label) { this.label = label; }

    public String label() { return label; }

    public boolean requiresAmount() { return this != LEAVE; }
}

/** 申請の状態(完成済み) */
enum RequestStatus {
    DRAFT("下書き"), SUBMITTED("申請中"), APPROVED("承認済"), RETURNED("差戻し"), WITHDRAWN("取下げ");

    private final String label;

    RequestStatus(String label) { this.label = label; }

    public String label() { return label; }
}

/** 申請(エンティティの縮小版。STS では JPA の @Entity で、申請者は Employee。完成済み) */
class ApprovalRequest {
    private Long id;
    private String title;
    private RequestType type;
    private Integer amount;
    private String reason;
    private RequestStatus status;
    private Long applicantId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public RequestType getType() { return type; }
    public void setType(RequestType type) { this.type = type; }
    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
}

/** 申請の登録と検索(完成済み) */
interface RequestService {
    /** 申請者 applicantId の下書きとして保存し、id の振られた申請を返す */
    ApprovalRequest createDraft(Long applicantId, ApprovalRequest request);

    /** 申請者 applicantId の申請だけを返す */
    List<ApprovalRequest> findMine(Long applicantId);
}

/** メモリ上の実装(テストで差し込む。完成済み) */
class InMemoryRequestService implements RequestService {
    private final List<ApprovalRequest> store = new ArrayList<>();

    @Override
    public ApprovalRequest createDraft(Long applicantId, ApprovalRequest request) {
        request.setId((long) store.size() + 1);
        request.setApplicantId(applicantId);
        request.setStatus(RequestStatus.DRAFT);
        store.add(request);
        return request;
    }

    @Override
    public List<ApprovalRequest> findMine(Long applicantId) {
        return store.stream()
                .filter(r -> r.getApplicantId().equals(applicantId))
                .toList();
    }

    List<ApprovalRequest> all() {
        return List.copyOf(store);
    }
}
