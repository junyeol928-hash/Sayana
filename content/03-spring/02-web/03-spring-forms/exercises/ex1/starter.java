import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SignupForm {

    // TODO: 必須(空白だけも不可)・20文字以内
    private String name;

    // TODO: 必須・メールアドレスの形式
    private String email;

    // TODO: 必須・18以上120以下(Integer なので @NotBlank は使えない点に注意)
    private Integer age;

    // TODO: 必須・「数字3桁-数字4桁」の形
    private String zipCode;

    // TODO: 必須・8文字以上
    private String password;

    private String passwordConfirm;

    // TODO: @AssertTrue を付けた public boolean isPasswordMatched() を作る
    //       (password が null なら false、password と passwordConfirm が等しければ true)

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
