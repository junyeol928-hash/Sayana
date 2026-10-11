import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RequestForm {

    @NotBlank(message = "件名を入力してください")
    @Size(max = 50, message = "件名は50文字以内で入力してください")
    private String title;

    @NotNull(message = "種類を選んでください")
    private RequestType type;

    @Min(value = 1, message = "金額は1円以上で入力してください")
    private Integer amount;

    @NotBlank(message = "理由を入力してください")
    private String reason;

    /** 相関チェック:経費精算・備品購入なら金額が必須 */
    @AssertTrue(message = "経費精算・備品購入は金額が必須です")
    public boolean isAmountEntered() {
        if (type == null || !type.requiresAmount()) {
            return true;   // 未選択は @NotNull、休暇は金額不要
        }
        return amount != null;
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
    EXPENSE("経費精算"),
    LEAVE("休暇"),
    PURCHASE("備品購入");

    private final String label;

    RequestType(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    /** 金額が必須の種類か(休暇は金額なし) */
    boolean requiresAmount() {
        return this != LEAVE;
    }
}
