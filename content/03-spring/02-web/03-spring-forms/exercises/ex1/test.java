import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignupFormTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /** すべて正しく入力されたフォーム */
    private static SignupForm valid() {
        SignupForm f = new SignupForm();
        f.setName("山田太郎");
        f.setEmail("taro@example.com");
        f.setAge(30);
        f.setZipCode("100-0001");
        f.setPassword("secret123");
        f.setPasswordConfirm("secret123");
        return f;
    }

    /** エラーになった項目名の一覧(順番が決まらないので並べ替える) */
    private static Set<String> errorFields(SignupForm f) {
        Set<String> fields = new TreeSet<>();
        for (ConstraintViolation<SignupForm> v : validator.validate(f)) {
            fields.add(v.getPropertyPath().toString());
        }
        return fields;
    }

    @Test
    @DisplayName("すべて正しく入力すればエラーは0件")
    void allValid() {
        assertEquals(Set.of(), errorFields(valid()),
                "正しい入力なのにエラーになった項目があります。ルールが厳しすぎないか確認してください");
    }

    @Test
    @DisplayName("全部未入力(null)なら、必須の5項目とパスワード確認がエラー")
    void allEmpty() {
        assertEquals(Set.of("age", "email", "name", "password", "passwordMatched", "zipCode"),
                errorFields(new SignupForm()),
                "未入力(null)でエラーにならない項目があります。@Size・@Pattern・@Email・@Min は null を OK とみなすので、"
                        + "@NotBlank(数値は @NotNull)と組み合わせてください。"
                        + "passwordMatched が無いなら @AssertTrue の isPasswordMatched() を確認してください");
    }

    @Test
    @DisplayName("氏名:空白だけはNG、20文字はOK・21文字はNG(境界値)")
    void name() {
        SignupForm f = valid();
        f.setName("   ");
        assertEquals(Set.of("name"), errorFields(f),
                "空白だけの氏名がエラーになりません。@NotEmpty ではなく @NotBlank を使ってください");
        f.setName("あ".repeat(20));
        assertEquals(Set.of(), errorFields(f), "20文字の氏名は OK です。@Size(max = 20) になっていますか?");
        f.setName("あ".repeat(21));
        assertEquals(Set.of("name"), errorFields(f), "21文字の氏名はエラーにしてください(@Size(max = 20))");
    }

    @Test
    @DisplayName("メール:@ が無い形式はNG")
    void email() {
        SignupForm f = valid();
        f.setEmail("taro.example.com");
        assertEquals(Set.of("email"), errorFields(f),
                "メールアドレスの形式チェックがありません。@Email を付けてください");
    }

    @Test
    @DisplayName("年齢:17はNG・18はOK、120はOK・121はNG(境界値)")
    void age() {
        SignupForm f = valid();
        f.setAge(17);
        assertEquals(Set.of("age"), errorFields(f), "17歳はエラーにしてください(@Min(18))");
        f.setAge(18);
        assertEquals(Set.of(), errorFields(f), "18歳は OK です。@Min の値を確認してください");
        f.setAge(120);
        assertEquals(Set.of(), errorFields(f), "120歳は OK です。@Max の値を確認してください");
        f.setAge(121);
        assertEquals(Set.of("age"), errorFields(f), "121歳はエラーにしてください(@Max(120))");
    }

    @Test
    @DisplayName("郵便番号とパスワード:形式・長さ・確認用の一致")
    void zipAndPassword() {
        SignupForm f = valid();
        f.setZipCode("1000001");
        assertEquals(Set.of("zipCode"), errorFields(f),
                "ハイフンの無い郵便番号はエラーにしてください(@Pattern(regexp = \"\\\\d{3}-\\\\d{4}\"))");

        f = valid();
        f.setPassword("abc1234");
        f.setPasswordConfirm("abc1234");
        assertEquals(Set.of("password"), errorFields(f), "7文字のパスワードはエラーにしてください(@Size(min = 8))");

        f = valid();
        f.setPasswordConfirm("secret124");
        Set<String> fields = errorFields(f);
        assertTrue(fields.contains("passwordMatched"),
                "確認用パスワードが違うのにエラーになりません。@AssertTrue を付けた isPasswordMatched() を作ってください");
        assertEquals(Set.of("passwordMatched"), fields, "パスワードの不一致以外のエラーが出ています");
    }
}
