import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class MemberControllerTest {

    private MemberStore store;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        store = new MemberStore();
        // @Validated を効かせるための Validator(Spring Boot なら自動で用意される)
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MemberController(store))
                .setValidator(validator)
                .build();
    }

    private MvcResult postForm(String name, String email) throws Exception {
        MockHttpServletRequestBuilder req = post("/members").param("name", name).param("email", email);
        return mockMvc.perform(req).andReturn();
    }

    /** 入力エラーで同じ画面に戻ったことを確かめて、BindingResult を返す */
    private BindingResult assertBackToForm(MvcResult r) {
        assertNotEquals(400, r.getResponse().getStatus(),
                "入力エラーで 400 Bad Request になっています。@Validated の付いた引数の『直後』に BindingResult を置いてください");
        ModelAndView mav = r.getModelAndView();
        assertNotNull(mav, "ビュー名が返っていません");
        assertEquals("members/form", mav.getViewName(),
                "入力エラーのときはリダイレクトせず、同じビュー名 \"members/form\" を返してください(引数に @Validated を付け忘れていませんか?)");
        Object br = mav.getModel().get(BindingResult.MODEL_KEY_PREFIX + "memberForm");
        assertInstanceOf(BindingResult.class, br, "Model に memberForm の BindingResult がありません。フォームは @ModelAttribute MemberForm form で受け取ってください");
        return (BindingResult) br;
    }

    @Test
    @DisplayName("GET /members/new:空の MemberForm を Model に入れて members/form を表示")
    void showForm() throws Exception {
        MvcResult r = mockMvc.perform(get("/members/new")).andReturn();
        assertEquals(200, r.getResponse().getStatus(), "GET /members/new は 200 OK を返してください");
        assertEquals("members/form", r.getModelAndView().getViewName(), "ビュー名は \"members/form\" です");
        Object form = r.getModelAndView().getModel().get("memberForm");
        assertNotNull(form, "Model に \"memberForm\" がありません。model.addAttribute(\"memberForm\", new MemberForm()) を書いてください");
        assertInstanceOf(MemberForm.class, form, "\"memberForm\" には MemberForm のインスタンスを入れてください");
    }

    @Test
    @DisplayName("正しい入力:登録して /members/done へリダイレクト、フラッシュ属性に「登録しました」")
    void registerSuccess() throws Exception {
        MvcResult r = postForm("山田太郎", "taro@example.com");
        assertEquals(302, r.getResponse().getStatus(), "登録に成功したら \"redirect:/members/done\" でリダイレクト(302)してください");
        assertEquals("/members/done", r.getResponse().getRedirectedUrl(), "リダイレクト先は /members/done です");
        assertEquals("登録しました", r.getFlashMap().get("message"),
                "フラッシュ属性 \"message\" に \"登録しました\" を入れてください(ra.addFlashAttribute)");
        assertEquals(List.of("山田太郎 <taro@example.com>"), store.findAll(),
                "正しい入力なら store.add(form.getName(), form.getEmail()) で1件登録してください");
    }

    @Test
    @DisplayName("氏名が空欄:登録せず、BindingResult に name のエラーを入れて members/form を返す")
    void blankName() throws Exception {
        BindingResult br = assertBackToForm(postForm("", "taro@example.com"));
        assertTrue(br.hasFieldErrors("name"), "氏名が空欄なのに name のエラーがありません");
        assertTrue(store.findAll().isEmpty(), "入力エラーなのに登録されています。hasErrors() のときは store.add を呼ばないでください");
    }

    @Test
    @DisplayName("メール形式エラー:email に Email エラーが付き、入力値は画面に残る")
    void invalidEmail() throws Exception {
        MvcResult r = postForm("山田太郎", "taro");
        BindingResult br = assertBackToForm(r);
        FieldError error = br.getFieldError("email");
        assertNotNull(error, "メールの形式が違うのに email のエラーがありません");
        assertEquals("Email", error.getCode(), "email のエラーは @Email の違反(コード \"Email\")のはずです");
        MemberForm form = (MemberForm) r.getModelAndView().getModel().get("memberForm");
        assertEquals("taro", form.getEmail(), "エラーで再表示するとき、入力した値はフォームに残っているはずです");
        assertTrue(store.findAll().isEmpty(), "入力エラーなのに登録されています");
    }

    @Test
    @DisplayName("氏名の長さ:21文字はエラー・20文字は登録できる(境界値)")
    void nameLength() throws Exception {
        BindingResult br = assertBackToForm(postForm("あ".repeat(21), "a@example.com"));
        assertTrue(br.hasFieldErrors("name"), "21文字の氏名は name のエラーになるはずです");

        MvcResult ok = postForm("あ".repeat(20), "a@example.com");
        assertEquals("/members/done", ok.getResponse().getRedirectedUrl(), "20文字の氏名は登録できて、/members/done へリダイレクトするはずです");
        assertEquals(1, store.findAll().size(), "20文字の氏名だけが登録され、21文字は登録されないはずです");
    }
}
