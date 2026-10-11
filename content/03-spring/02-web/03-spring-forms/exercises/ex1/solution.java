import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SignupForm {

    @NotBlank(message = "氏名を入力してください")
    @Size(max = 20, message = "氏名は20文字以内で入力してください")
    private String name;

    @NotBlank(message = "メールアドレスを入力してください")
    @Email(message = "メールアドレスの形式が正しくありません")
    private String email;

    @NotNull(message = "年齢を入力してください")
    @Min(value = 18, message = "18歳以上の方が対象です")
    @Max(value = 120, message = "年齢を正しく入力してください")
    private Integer age;

    @NotBlank(message = "郵便番号を入力してください")
    @Pattern(regexp = "\\d{3}-\\d{4}", message = "郵便番号は 123-4567 の形で入力してください")
    private String zipCode;

    @NotBlank(message = "パスワードを入力してください")
    @Size(min = 8, message = "パスワードは8文字以上にしてください")
    private String password;

    private String passwordConfirm;

    @AssertTrue(message = "確認用パスワードが一致しません")
    public boolean isPasswordMatched() {
        return password != null && password.equals(passwordConfirm);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPasswordConfirm() { return passwordConfirm; }
    public void setPasswordConfirm(String passwordConfirm) { this.passwordConfirm = passwordConfirm; }
}
