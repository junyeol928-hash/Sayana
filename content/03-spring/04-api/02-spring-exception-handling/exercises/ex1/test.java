import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(new OrderService()))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    /** 例外が ApiExceptionHandler で処理されずに漏れてきたら、分かりやすいメッセージで落とす */
    private MockHttpServletResponse call(RequestBuilder request) {
        try {
            return mvc.perform(request).andReturn().getResponse();
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            fail("例外が Controller の外に漏れています(" + root.getClass().getSimpleName()
                    + ")。ApiExceptionHandler のメソッドに @ExceptionHandler(例外クラス.class) を付けましたか?");
            return null;
        }
    }

    private void assertJson(MockHttpServletResponse res) {
        assertNotNull(res.getContentType(), "エラーのときも JSON の本文を返してください(Content-Type が空です)");
        assertTrue(res.getContentType().startsWith("application/json"),
                "Content-Type が application/json ではありません: " + res.getContentType());
    }

    @Test
    @DisplayName("正常な GET は今までどおり 200(例外ハンドラが邪魔をしない)")
    void normal() throws Exception {
        MockHttpServletResponse res = call(get("/api/orders/1"));
        assertEquals(200, res.getStatus(), "存在する注文 1 の取得は 200 のはずです");
        mvc.perform(get("/api/orders/1")).andExpect(jsonPath("$.item").value("ボールペン×10"));
    }

    @Test
    @DisplayName("存在しない注文は 404 + {code: ORDER_NOT_FOUND, message: ...}")
    void notFound() throws Exception {
        MockHttpServletResponse res = call(get("/api/orders/99"));
        assertEquals(404, res.getStatus(), "OrderNotFoundException は 404 にしてください");
        assertJson(res);
        mvc.perform(get("/api/orders/99"))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("注文 99 は見つかりません"));
    }

    @Test
    @DisplayName("キャンセルの途中で見つからなくても同じく 404(どこで投げても同じハンドラが受ける)")
    void notFoundOnCancel() {
        MockHttpServletResponse res = call(post("/api/orders/77/cancel"));
        assertEquals(404, res.getStatus(), "cancel の中で投げられた OrderNotFoundException も 404 にしてください");
    }

    @Test
    @DisplayName("発送済みの注文のキャンセルは 409 + ORDER_STATE_CONFLICT")
    void conflict() throws Exception {
        assertEquals(200, call(post("/api/orders/1/cancel")).getStatus(), "未発送の注文 1 はキャンセルできる(200)はずです");
        MockHttpServletResponse res = call(post("/api/orders/2/cancel"));
        assertEquals(409, res.getStatus(), "OrderStateException は 409 Conflict にしてください");
        assertJson(res);
        mvc.perform(post("/api/orders/2/cancel"))
                .andExpect(jsonPath("$.code").value("ORDER_STATE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("注文 2 は発送済みのためキャンセルできません"));
    }

    @Test
    @DisplayName("想定外の例外は 500 + INTERNAL_ERROR、内部の詳細は見せない")
    void unexpected() throws Exception {
        MockHttpServletResponse res = call(get("/api/orders/1/receipt"));
        assertEquals(500, res.getStatus(), "想定外の例外(IllegalStateException)は 500 にしてください");
        assertJson(res);
        String body = res.getContentAsString();
        assertFalse(body.contains("10.0.3.7") || body.contains("timeout"),
                "500 の本文に例外のメッセージ(内部の URL)が出ています。利用者には固定の安全なメッセージだけを返してください: " + body);
        mvc.perform(get("/api/orders/1/receipt"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("システムエラーが発生しました"));
    }
}
