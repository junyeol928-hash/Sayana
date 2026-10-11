import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.jayway.jsonpath.JsonPath;

class ApiExceptionHandlerTest {

    static final String SECRET = "DB接続に失敗: jdbc:h2:file:./data/approval user=sa password=s3cret";

    /** テスト用のメモリ実装。ID 500 は「DB が落ちている」想定で想定外の例外を投げる */
    static class MemoryRequestService implements RequestService {
        final Map<Long, RequestDetail> store = Map.of(
                1L, new RequestDetail(1L, "出張交通費(大阪)", RequestStatus.SUBMITTED),
                3L, new RequestDetail(3L, "モニター購入", RequestStatus.APPROVED));

        @Override
        public List<RequestDetail> findByStatus(RequestStatus status) {
            return store.values().stream()
                    .filter(r -> status == null || r.status() == status)
                    .toList();
        }

        @Override
        public RequestDetail get(Long id) {
            if (id == 500L) {
                throw new IllegalStateException(SECRET);
            }
            RequestDetail r = store.get(id);
            if (r == null) {
                throw new RequestNotFoundException(id);
            }
            return r;
        }

        @Override
        public RequestDetail approve(Long id, String loginId) {
            RequestDetail r = get(id);
            if (r.status() != RequestStatus.SUBMITTED) {
                throw new BusinessRuleException("承認済の申請は変更できません");
            }
            return new RequestDetail(id, r.title(), RequestStatus.APPROVED);
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RequestApiController(new MemoryRequestService()))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    /** 例外が外まで飛び出したら、何を直せばよいかを添えて落とす */
    private ResultActions perform(MockHttpServletRequestBuilder req) {
        try {
            return mvc.perform(req);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            fail(cause.getClass().getSimpleName() + " を受け止めるハンドラがありません。"
                    + "ApiExceptionHandler に @RestControllerAdvice と @ExceptionHandler を付けてください");
            return null;
        }
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder req) {
        return perform(req).andReturn().getResponse();
    }

    /** 共通形式(ProblemDetail)になっているか */
    private void assertProblem(MockHttpServletResponse res, int status, String title) throws Exception {
        assertEquals(status, res.getStatus(), "ステータスコードが違います");
        String type = res.getContentType();
        assertNotNull(type, "Content-Type が空です。ProblemDetail を返していますか?");
        assertTrue(type.startsWith("application/problem+json"),
                "エラーは共通形式(ProblemDetail、Content-Type は application/problem+json)で返します。実際: " + type);
        String body = res.getContentAsString();
        assertEquals(title, JsonPath.read(body, "$.title"), "title が違います");
        assertEquals(status, (int) JsonPath.read(body, "$.status"), "本文の status が違います");
    }

    @Test
    @DisplayName("正常なリクエストはそのまま 200(ハンドラは正常系に影響しない)")
    void ok() throws Exception {
        assertEquals(200, call(get("/api/requests/1")).getStatus(), "GET /api/requests/1 は 200 のはずです");
        perform(post("/api/requests/1/approve").principal(() -> "moka@example.com"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("BusinessRuleException は 409・title「業務ルール違反」・detail は例外メッセージ")
    void businessRule() throws Exception {
        MockHttpServletResponse res = call(post("/api/requests/3/approve").principal(() -> "moka@example.com"));
        assertProblem(res, 409, "業務ルール違反");
        assertEquals("承認済の申請は変更できません", JsonPath.read(res.getContentAsString(), "$.detail"),
                "409 の detail には BusinessRuleException のメッセージをそのまま入れます");
    }

    @Test
    @DisplayName("存在しない申請は 404・title「申請が見つかりません」(詳細でも承認でも)")
    void notFound() throws Exception {
        MockHttpServletResponse res = call(get("/api/requests/99"));
        assertProblem(res, 404, "申請が見つかりません");
        assertEquals("申請(ID=99)が見つかりません", JsonPath.read(res.getContentAsString(), "$.detail"),
                "404 の detail には RequestNotFoundException のメッセージを入れます");
        assertProblem(call(post("/api/requests/99/approve").principal(() -> "moka@example.com")),
                404, "申請が見つかりません");
    }

    @Test
    @DisplayName("想定外の例外は 500・title「システムエラー」で、例外メッセージ(接続先やパスワード)を漏らさない")
    void unexpectedHidesDetails() throws Exception {
        MockHttpServletResponse res = call(get("/api/requests/500"));
        assertProblem(res, 500, "システムエラー");
        String body = res.getContentAsString();
        assertEquals("時間をおいて再度お試しください", JsonPath.read(body, "$.detail"),
                "500 の detail は決まった文言にします");
        for (String leak : List.of("jdbc", "password", "s3cret", "IllegalStateException", "DB接続")) {
            assertFalse(body.contains(leak),
                    "500 の本文に例外の中身(" + leak + ")が漏れています。e.getMessage() は返さず、ログにだけ出します: " + body);
        }
    }

    @Test
    @DisplayName("500 には errorId(16進8文字)が付き、毎回違う値になる")
    void errorId() throws Exception {
        String body1 = call(get("/api/requests/500")).getContentAsString();
        String body2 = call(get("/api/requests/500")).getContentAsString();
        Object id1 = JsonPath.read(body1, "$.errorId");
        Object id2 = JsonPath.read(body2, "$.errorId");
        assertTrue(id1 instanceof String s && s.matches("[0-9a-f]{8}"),
                "errorId は UUID.randomUUID().toString().substring(0, 8) のような16進8文字にします。実際: " + id1);
        assertNotEquals(id1, id2, "errorId は例外が起きるたびに新しく作ります(問い合わせとログを1件ずつ結びつけるため)");
    }

    @Test
    @DisplayName("Spring 標準のエラー(型変換の失敗 400・メソッド違い 405)は 500 にしない")
    void springErrorsStayAsIs() throws Exception {
        assertEquals(500, call(get("/api/requests/500")).getStatus(),
                "まず想定外の例外を 500 にするハンドラ(@ExceptionHandler(Exception.class))を書いてください");
        assertEquals(400, call(get("/api/requests?status=submitted")).getStatus(),
                "?status=submitted(小文字)は入力ミスなので 400 のままにします。"
                        + "Exception を受けるハンドラだけだと 500 になるので、ResponseEntityExceptionHandler を継承してください");
        assertEquals(405, call(delete("/api/requests/1")).getStatus(),
                "対応していない HTTP メソッドは 405 のままにします(ResponseEntityExceptionHandler を継承)");
    }
}
