import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class RequestControllerTest {

    private InMemoryRequestService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = new InMemoryRequestService();
        // @Validated を効かせるための Validator(Spring Boot なら自動で用意される)
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new RequestController(service))
                .setValidator(validator)
                .build();
    }

    private MvcResult postForm(String title, String type, String amount, String reason) throws Exception {
        return mockMvc.perform(post("/requests")
                .param("title", title).param("type", type)
                .param("amount", amount).param("reason", reason)).andReturn();
    }

    /** 入力エラーで同じ画面に戻ったことを確かめて、BindingResult を返す */
    private BindingResult assertBackToForm(MvcResult r) {
        assertNotEquals(400, r.getResponse().getStatus(),
                "入力エラーで 400 Bad Request になっています。@Validated の付いた引数の『直後』に BindingResult を置いてください");
        ModelAndView mav = r.getModelAndView();
        assertNotNull(mav, "ビュー名が返っていません");
        assertEquals("requests/new", mav.getViewName(),
                "入力エラーのときはリダイレクトせず \"requests/new\" を返してください(引数に @Validated を付け忘れていませんか?)");
        assertNotNull(mav.getModel().get("types"),
                "エラーで画面に戻ったとき Model に \"types\" がありません。セレクトボックスが空になります。@ModelAttribute(\"types\") のメソッドを作ってください");
        Object br = mav.getModel().get(BindingResult.MODEL_KEY_PREFIX + "requestForm");
        assertInstanceOf(BindingResult.class, br,
                "Model に requestForm の BindingResult がありません。フォームは @ModelAttribute RequestForm form で受け取ってください");
        assertTrue(service.all().isEmpty(), "入力エラーなのに保存されています。hasErrors() のときは createDraft を呼ばないでください");
        return (BindingResult) br;
    }

    @Test
    @DisplayName("GET /requests/new:空の RequestForm と種類の選択肢 types を Model に入れて requests/new を表示")
    void showForm() throws Exception {
        MvcResult r = mockMvc.perform(get("/requests/new")).andReturn();
        assertEquals(200, r.getResponse().getStatus(), "GET /requests/new は 200 OK を返してください");
        ModelAndView mav = r.getModelAndView();
        assertEquals("requests/new", mav.getViewName(), "ビュー名は \"requests/new\" です");
        assertInstanceOf(RequestForm.class, mav.getModel().get("requestForm"),
                "Model に \"requestForm\" として空の RequestForm を入れてください");
        assertArrayEquals(RequestType.values(), (Object[]) mav.getModel().get("types"),
                "Model の \"types\" には RequestType.values() を入れてください");
    }

    @Test
    @DisplayName("正しい入力:社員ID 1 の下書きとして保存し、/requests へリダイレクト(PRG)")
    void createSuccess() throws Exception {
        MvcResult r = postForm("客先訪問の交通費", "EXPENSE", "1200", "A社打合せ");
        assertEquals(302, r.getResponse().getStatus(), "保存に成功したら \"redirect:/requests\" でリダイレクト(302)してください");
        assertEquals("/requests", r.getResponse().getRedirectedUrl(), "リダイレクト先は /requests です");
        assertEquals(1, service.all().size(), "正しい入力なら createDraft で1件保存してください");
        ApprovalRequest saved = service.all().get(0);
        assertEquals(1L, saved.getApplicantId(), "申請者は CURRENT_EMPLOYEE_ID(1)で保存してください");
        assertEquals("客先訪問の交通費", saved.getTitle(), "フォームの値を form.toEntity() で詰め替えて渡してください");
        assertEquals(1200, saved.getAmount(), "金額 1200 が保存されていません");
        assertEquals("下書きを保存しました(No.1)", r.getFlashMap().get("message"),
                "フラッシュ属性 \"message\" に \"下書きを保存しました(No.<id>)\" を入れてください(ra.addFlashAttribute)");
    }

    @Test
    @DisplayName("件名が空・理由が空白:保存せず requests/new に戻り、選択肢 types も残っている")
    void blankFields() throws Exception {
        BindingResult br = assertBackToForm(postForm("", "EXPENSE", "1200", "  "));
        assertTrue(br.hasFieldErrors("title"), "件名が空なのに title のエラーがありません");
        assertTrue(br.hasFieldErrors("reason"), "理由が空白だけなのに reason のエラーがありません");
    }

    @Test
    @DisplayName("相関チェック:経費精算で金額なしはエラー、休暇で金額なしは保存できる")
    void amountByType() throws Exception {
        BindingResult br = assertBackToForm(postForm("交通費", "EXPENSE", "", "打合せ"));
        assertTrue(br.hasFieldErrors("amountEntered"), "経費精算で金額が空なのに amountEntered のエラーがありません");

        MvcResult ok = postForm("夏季休暇", "LEAVE", "", "帰省");
        assertEquals("/requests", ok.getResponse().getRedirectedUrl(), "休暇は金額なしで保存でき、/requests へリダイレクトするはずです");
        assertEquals(1, service.all().size(), "休暇の申請だけが保存されるはずです");
        assertNull(service.all().get(0).getAmount(), "休暇の金額は null で保存されるはずです");
    }

    @Test
    @DisplayName("GET /requests:社員ID 1 の申請だけを requests に入れて requests/list を表示")
    void listMine() throws Exception {
        ApprovalRequest mine1 = service.createDraft(1L, new ApprovalRequest());
        service.createDraft(2L, new ApprovalRequest());   // 他人の申請
        ApprovalRequest mine2 = service.createDraft(1L, new ApprovalRequest());

        MvcResult r = mockMvc.perform(get("/requests")).andReturn();
        assertEquals("requests/list", r.getModelAndView().getViewName(), "ビュー名は \"requests/list\" です");
        Object requests = r.getModelAndView().getModel().get("requests");
        assertNotNull(requests, "Model に \"requests\" がありません。findMine(CURRENT_EMPLOYEE_ID) の結果を入れてください");
        assertEquals(List.of(mine1, mine2), requests,
                "一覧には社員ID 1(CURRENT_EMPLOYEE_ID)の申請だけを出してください。他人の申請が混ざっていませんか?");
    }
}
