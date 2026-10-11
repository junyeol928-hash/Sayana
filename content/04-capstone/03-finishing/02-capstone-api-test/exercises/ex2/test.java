import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.SerializationFeature;

class RequestApiControllerTest {

    static final Employee HARU = new Employee(1L, "ハル", "haru@example.com");
    static final Employee MOKA = new Employee(2L, "モカ", "moka@example.com");
    static final Employee BOSS = new Employee(3L, "ボス", "boss@example.com");

    /** テスト用のメモリ実装(STS では ApprovalService が業務ルール1〜6を守って DB を更新する) */
    static class MemoryRequestService implements RequestService {
        final Map<Long, ApprovalRequest> store = new HashMap<>();
        final Map<String, Employee> employees = Map.of(
                HARU.email(), HARU, MOKA.email(), MOKA, BOSS.email(), BOSS);

        @Override
        public ApprovalRequest get(Long id) {
            ApprovalRequest r = store.get(id);
            if (r == null) {
                throw new RequestNotFoundException(id);
            }
            return r;
        }

        @Override
        public ApprovalRequest approve(Long id, String loginId) {
            ApprovalRequest r = get(id);
            if (r.applicant().email().equals(loginId)) {
                throw new BusinessRuleException("自分の申請は承認できません");
            }
            if (r.status() == RequestStatus.APPROVED) {
                throw new BusinessRuleException("承認済の申請は変更できません");
            }
            if (r.status() != RequestStatus.SUBMITTED) {
                throw new BusinessRuleException("この状態の申請は「承認」できません");
            }
            ApprovalRequest done = new ApprovalRequest(r.id(), r.title(), r.type(), r.amount(),
                    r.reason(), RequestStatus.APPROVED, r.applicant(), employees.get(loginId),
                    null, r.createdAt(), r.submittedAt(), LocalDateTime.of(2026, 10, 9, 17, 0));
            store.put(id, done);
            return done;
        }
    }

    private MemoryRequestService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 1, 9, 0);
        service = new MemoryRequestService();
        service.store.put(1L, new ApprovalRequest(1L, "出張交通費(大阪)", RequestType.EXPENSE, 12800,
                "大阪支店での打合せ", RequestStatus.SUBMITTED, HARU, null, null, t, t, null));
        service.store.put(2L, new ApprovalRequest(2L, "技術書の購入", RequestType.PURCHASE, 5000,
                "チームで読む", RequestStatus.SUBMITTED, MOKA, null, null, t, t, null));
        service.store.put(3L, new ApprovalRequest(3L, "モニター購入", RequestType.PURCHASE, 45000,
                "在宅勤務用", RequestStatus.APPROVED, HARU, MOKA, null, t, t, t));

        var mapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mvc = MockMvcBuilders.standaloneSetup(new RequestApiController(service))
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter(mapper))
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    /** ログイン中の人(STS では Spring Security が入れてくれる) */
    private static Principal user(String loginId) {
        return () -> loginId;
    }

    /** 例外が Controller の外まで飛び出したら、何を直せばよいかを添えて落とす */
    private ResultActions perform(MockHttpServletRequestBuilder req) {
        try {
            return mvc.perform(req);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            fail(cause.getClass().getSimpleName() + " が Controller の外まで飛び出しました。"
                    + "@ExceptionHandler を付けたメソッドで ProblemDetail を返してください: " + cause.getMessage());
            return null;
        }
    }

    private ResultActions approve(long id, String loginId) {
        return perform(post("/api/requests/" + id + "/approve").principal(user(loginId)));
    }

    private void assertProblem(ResultActions actions, int status) {
        MockHttpServletResponse res = actions.andReturn().getResponse();
        assertEquals(status, res.getStatus(), "ステータスコードが違います");
        String type = res.getContentType();
        assertNotNull(type, "Content-Type が空です。ProblemDetail を返していますか?");
        assertTrue(type.startsWith("application/problem+json"),
                "ProblemDetail を返すと Content-Type は application/problem+json になります。実際: " + type);
    }

    @Test
    @DisplayName("申請中の申請をモカが承認すると 200・APPROVED・承認者名がモカ")
    void approveOk() throws Exception {
        MockHttpServletResponse res = approve(1, "moka@example.com").andReturn().getResponse();
        assertEquals(200, res.getStatus(),
                "POST /api/requests/1/approve は 200 のはずです。approve に @PostMapping(\"/{id}/approve\") と @PathVariable を付けましたか?");
        perform(get("/api/requests/1"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approverName").value("モカ"));
        assertEquals(RequestStatus.APPROVED, service.store.get(1L).status(),
                "service.approve を呼んで承認してください");
    }

    @Test
    @DisplayName("承認したのが誰か(principal.getName())を Service に渡している")
    void approverIsLoginUser() throws Exception {
        approve(1, "boss@example.com")
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approverName").value("ボス"))
                .andExpect(jsonPath("$.title").value("出張交通費(大阪)"));
    }

    @Test
    @DisplayName("承認済の申請をもう一度承認すると 409 の ProblemDetail")
    void alreadyApproved() throws Exception {
        assertProblem(approve(3, "boss@example.com"), 409);
        approve(3, "boss@example.com")
                .andExpect(jsonPath("$.title").value("業務ルール違反"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("承認済の申請は変更できません"));
    }

    @Test
    @DisplayName("自分の申請を承認しようとすると 409、メッセージは Service の例外のまま")
    void ownRequest() throws Exception {
        assertProblem(approve(2, "moka@example.com"), 409);
        approve(2, "moka@example.com")
                .andExpect(jsonPath("$.detail").value("自分の申請は承認できません"));
        assertEquals(RequestStatus.SUBMITTED, service.store.get(2L).status(),
                "ルール違反のときは状態が変わってはいけません");
    }

    @Test
    @DisplayName("存在しない申請は承認でも詳細でも 404 の ProblemDetail")
    void notFound() throws Exception {
        assertProblem(approve(99, "moka@example.com"), 404);
        approve(99, "moka@example.com")
                .andExpect(jsonPath("$.title").value("申請が見つかりません"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("申請(ID=99)が見つかりません"));
        assertProblem(perform(get("/api/requests/99")), 404);
    }
}
