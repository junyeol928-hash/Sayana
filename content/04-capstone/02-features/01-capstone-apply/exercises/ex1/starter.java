import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RequestForm {

    // TODO: 必須(空白だけも不可)・50文字以内の注釈を付ける(メッセージは問題文の表のとおり)
    private String title;

    // TODO: 必須の注釈を付ける
    private RequestType type;

    // TODO: 1以上の注釈を付ける(必須かどうかは種類で変わるので、ここには付けない)
    private Integer amount;

    // TODO: 必須(空白だけも不可)の注釈を付ける
    private String reason;

    // TODO: @AssertTrue(message = "経費精算・備品購入は金額が必須です") を付ける
    public boolean isAmountEntered() {
        // TODO: 経費精算・備品購入で amount が null なら false。休暇・種類未選択なら true
        return true;
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
