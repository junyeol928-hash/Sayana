import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ContactControllerTest {

    private ContactController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new ContactController();
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    private MvcResult send(RequestBuilder req) throws Exception {
        return mvc.perform(req).andReturn();
    }

    private String viewName(MvcResult result) {
        return result.getModelAndView() == null ? null : result.getModelAndView().getViewName();
    }

    @Test
    @DisplayName("GET /contact はビュー名 contact/form を返す")
    void form() throws Exception {
        MvcResult result = send(get("/contact"));
        assertEquals(200, result.getResponse().getStatus(),
                "GET /contact が 200 になりません。@Controller・@RequestMapping(\"/contact\")・@GetMapping を確認してください");
        assertEquals("contact/form", viewName(result),
                "GET /contact はビュー名 \"contact/form\" を返してください(先頭に / は付けません)");
    }

    @Test
    @DisplayName("POST /contact は保存して /contact/thanks へ 302 リダイレクト")
    void submitRedirects() throws Exception {
        MvcResult result = send(post("/contact").param("name", "ハル").param("message", "パスワードを忘れました"));
        assertEquals(302, result.getResponse().getStatus(),
                "POST /contact は \"redirect:/contact/thanks\" を返してリダイレクト(302)してください");
        assertEquals("/contact/thanks", result.getResponse().getRedirectedUrl(),
                "リダイレクト先(Location)は /contact/thanks にしてください");
        assertEquals(1, controller.inquiries.size(), "POST /contact で問い合わせを inquiries に1件追加してください");
        assertEquals(new Inquiry("ハル", "パスワードを忘れました"), controller.inquiries.get(0),
                "保存した Inquiry の name / message が送られた値と違います");
    }

    @Test
    @DisplayName("GET /contact/thanks はビュー名 contact/thanks を返し、何も保存しない")
    void thanks() throws Exception {
        MvcResult result = send(get("/contact/thanks"));
        assertEquals(200, result.getResponse().getStatus(),
                "GET /contact/thanks が 200 になりません。@GetMapping(\"/thanks\") を確認してください");
        assertEquals("contact/thanks", viewName(result), "完了画面のビュー名は \"contact/thanks\" です");
        assertTrue(controller.inquiries.isEmpty(), "完了画面を表示するだけで問い合わせを保存してはいけません");
    }

    @Test
    @DisplayName("2回 POST すると2件たまる(完了画面の再読み込みでは増えない)")
    void twoPosts() throws Exception {
        send(post("/contact").param("name", "A").param("message", "1件目"));
        send(post("/contact").param("name", "B").param("message", "2件目"));
        send(get("/contact/thanks"));
        send(get("/contact/thanks"));
        assertEquals(2, controller.inquiries.size(),
                "POST した回数ぶんだけ保存されるはずです(完了画面の GET では増えない)");
        assertEquals("B", controller.inquiries.get(1).name(), "2件目は後から送った B のはずです");
    }

    @Test
    @DisplayName("message が無い POST は 400 で、何も保存しない")
    void missingMessage() throws Exception {
        MvcResult result = send(post("/contact").param("name", "ハル"));
        assertEquals(400, result.getResponse().getStatus(),
                "message が無いときは 400 になるはずです。@RequestParam を required = false にしていませんか?");
        assertTrue(controller.inquiries.isEmpty(), "400 のときは何も保存しないでください");
    }
}
