import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ValidationErrorHandlerTest {

    private MockMvc mvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new MemberController())
                .setControllerAdvice(new ValidationErrorHandler())
                .setValidator(validator)
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    private ResultActions register(String json) throws Exception {
        return mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** 400 の ProblemDetail になっているかをまとめて確かめる */
    private void assertProblem(MockHttpServletResponse res) throws Exception {
        assertEquals(400, res.getStatus(), "入力エラーは 400 Bad Request です");
        assertFalse(res.getContentAsString().isEmpty(),
                "400 の本文が空です。@ExceptionHandler(MethodArgumentNotValidException.class) を付けて ProblemDetail を返してください");
        String type = res.getContentType();
        assertNotNull(type, "Content-Type が空です");
        assertTrue(type.startsWith("application/problem+json"),
                "ProblemDetail を返すと Content-Type は application/problem+json になります。実際: " + type);
    }

    @Test
    @DisplayName("正しい入力なら 201(ハンドラは正常系に影響しない)")
    void valid() throws Exception {
        MockHttpServletResponse res = register(
                "{\"name\":\"山田花子\",\"email\":\"hanako@example.com\",\"age\":30}").andReturn().getResponse();
        assertEquals(201, res.getStatus(), "正しい入力なら 201 Created のはずです");
    }

    @Test
    @DisplayName("氏名が空なら 400 の ProblemDetail(title・status・detail・errors.name)")
    void blankName() throws Exception {
        String json = "{\"name\":\"\",\"email\":\"hanako@example.com\",\"age\":30}";
        assertProblem(register(json).andReturn().getResponse());
        register(json)
                .andExpect(jsonPath("$.title").value("入力エラー"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("入力内容を確認してください"))
                .andExpect(jsonPath("$.errors.name").value("氏名は必須です"));
    }

    @Test
    @DisplayName("メール形式と年齢の2項目がダメなら、errors に2つとも入る")
    void twoErrors() throws Exception {
        String json = "{\"name\":\"佐藤一郎\",\"email\":\"sato-at-example\",\"age\":17}";
        assertProblem(register(json).andReturn().getResponse());
        register(json)
                .andExpect(jsonPath("$.errors.email").value("メールアドレスの形式が正しくありません"))
                .andExpect(jsonPath("$.errors.age").value("18歳以上の方のみ登録できます"))
                .andExpect(jsonPath("$.errors.name").doesNotExist());
    }

    @Test
    @DisplayName("年齢 18 はOK・121 はNG(境界値)")
    void ageBoundary() throws Exception {
        MockHttpServletResponse ok = register(
                "{\"name\":\"高橋\",\"email\":\"t@example.com\",\"age\":18}").andReturn().getResponse();
        assertEquals(201, ok.getStatus(), "18歳ちょうどは登録できるはずです");
        String json = "{\"name\":\"高橋\",\"email\":\"t@example.com\",\"age\":121}";
        assertProblem(register(json).andReturn().getResponse());
        register(json).andExpect(jsonPath("$.errors.age").value("年齢は120以下で入力してください"));
    }

    @Test
    @DisplayName("空の JSON({})なら3項目すべての「必須」メッセージが出る")
    void allMissing() throws Exception {
        assertProblem(register("{}").andReturn().getResponse());
        register("{}")
                .andExpect(jsonPath("$.errors.name").value("氏名は必須です"))
                .andExpect(jsonPath("$.errors.email").value("メールアドレスは必須です"))
                .andExpect(jsonPath("$.errors.age").value("年齢は必須です"));
    }
}
