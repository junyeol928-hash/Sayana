import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestFormTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private static RequestForm form(String title, RequestType type, Integer amount, String reason) {
        RequestForm f = new RequestForm();
        f.setTitle(title);
        f.setType(type);
        f.setAmount(amount);
        f.setReason(reason);
        return f;
    }

    /** 項目名 → エラーメッセージの一覧 */
    private static Map<String, List<String>> errors(RequestForm f) {
        Map<String, List<String>> map = new TreeMap<>();
        for (ConstraintViolation<RequestForm> v : validator.validate(f)) {
            map.computeIfAbsent(v.getPropertyPath().toString(), k -> new ArrayList<>()).add(v.getMessage());
        }
        return map;
    }

    @Test
    @DisplayName("正しい入力:経費精算 1,200円、休暇(金額なし)はどちらもエラーなし")
    void validInput() {
        assertEquals(Map.of(), errors(form("客先訪問の交通費", RequestType.EXPENSE, 1200, "A社打合せ")),
                "正しい経費精算の申請にエラーが出ています");
        assertEquals(Map.of(), errors(form("夏季休暇", RequestType.LEAVE, null, "帰省")),
                "休暇は金額なしで正しい申請です。isAmountEntered() は休暇なら true を返してください");
    }

    @Test
    @DisplayName("件名:空・空白だけはエラー、50文字はOK、51文字はエラー(境界値)")
    void title() {
        assertEquals(List.of("件名を入力してください"), errors(form("", RequestType.EXPENSE, 100, "理由")).get("title"),
                "件名が空のときは title に「件名を入力してください」を出してください(@NotBlank)");
        assertEquals(List.of("件名を入力してください"), errors(form("   ", RequestType.EXPENSE, 100, "理由")).get("title"),
                "空白だけの件名もエラーです。@NotEmpty ではなく @NotBlank を使ってください");
        assertFalse(errors(form("あ".repeat(50), RequestType.EXPENSE, 100, "理由")).containsKey("title"),
                "50文字ちょうどの件名はOKのはずです(@Size(max = 50))");
        assertEquals(List.of("件名は50文字以内で入力してください"),
                errors(form("あ".repeat(51), RequestType.EXPENSE, 100, "理由")).get("title"),
                "51文字の件名は title に「件名は50文字以内で入力してください」を出してください");
    }

    @Test
    @DisplayName("種類:未選択(null)はエラー。金額の相関チェックは重ねて出さない")
    void typeRequired() {
        Map<String, List<String>> e = errors(form("件名", null, null, "理由"));
        assertEquals(List.of("種類を選んでください"), e.get("type"),
                "種類が null のときは type に「種類を選んでください」を出してください(@NotNull)");
        assertFalse(e.containsKey("amountEntered"),
                "種類が未選択のときは isAmountEntered() は true を返してください(エラーは type の1つだけにする)");
    }

    @Test
    @DisplayName("相関チェック:経費精算・備品購入で金額なしはエラー、休暇はOK")
    void amountRequiredByType() {
        String msg = "経費精算・備品購入は金額が必須です";
        assertEquals(List.of(msg), errors(form("交通費", RequestType.EXPENSE, null, "理由")).get("amountEntered"),
                "経費精算で金額が null のときは amountEntered のエラーにしてください(@AssertTrue と isAmountEntered())");
        assertEquals(List.of(msg), errors(form("モニター", RequestType.PURCHASE, null, "理由")).get("amountEntered"),
                "備品購入で金額が null のときも amountEntered のエラーです");
        assertFalse(errors(form("有給休暇", RequestType.LEAVE, null, "理由")).containsKey("amountEntered"),
                "休暇は金額が無くてもエラーにしないでください");
    }

    @Test
    @DisplayName("金額:0円と-100円はエラー、1円はOK(境界値)")
    void amountMin() {
        assertEquals(List.of("金額は1円以上で入力してください"),
                errors(form("交通費", RequestType.EXPENSE, 0, "理由")).get("amount"),
                "0円は amount に「金額は1円以上で入力してください」を出してください(@Min(1))");
        assertTrue(errors(form("交通費", RequestType.EXPENSE, -100, "理由")).containsKey("amount"),
                "マイナスの金額もエラーのはずです");
        assertEquals(Map.of(), errors(form("交通費", RequestType.EXPENSE, 1, "理由")),
                "1円ちょうどはOKのはずです");
    }

    @Test
    @DisplayName("理由:空白だけはエラー")
    void reasonRequired() {
        assertEquals(List.of("理由を入力してください"), errors(form("交通費", RequestType.EXPENSE, 500, "  ")).get("reason"),
                "理由が空白だけのときは reason に「理由を入力してください」を出してください(@NotBlank)");
    }
}
