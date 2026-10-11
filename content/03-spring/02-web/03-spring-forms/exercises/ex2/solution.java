import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/members")
public class MemberController {

    private final MemberStore store;

    public MemberController(MemberStore store) {
        this.store = store;
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("memberForm", new MemberForm());
        return "members/form";
    }

    @PostMapping
    public String register(@Validated @ModelAttribute MemberForm form,
                           BindingResult result,
                           RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "members/form";            // 入力値とエラーを持ったまま同じ画面へ
        }
        store.add(form.getName(), form.getEmail());
        ra.addFlashAttribute("message", "登録しました");
        return "redirect:/members/done";      // PRG:POST の後はリダイレクト
    }
}

/** 会員登録フォーム(この演習では完成済み) */
class MemberForm {

    @NotBlank(message = "氏名を入力してください")
    @Size(max = 20, message = "氏名は20文字以内で入力してください")
    private String name;

    @NotBlank(message = "メールアドレスを入力してください")
    @Email(message = "メールアドレスの形式が正しくありません")
    private String email;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}

/** 登録した会員を覚えておくだけの入れ物(この演習では完成済み) */
class MemberStore {

    private final List<String> members = new ArrayList<>();

    void add(String name, String email) {
        members.add(name + " <" + email + ">");
    }

    List<String> findAll() {
        return List.copyOf(members);
    }
}
