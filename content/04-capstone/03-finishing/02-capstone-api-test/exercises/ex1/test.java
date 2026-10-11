import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RequestApiControllerTest {

    /** テスト用のメモリ実装(STS では RequestQueryService + Repository が DB から読む) */
    static class MemoryRequestService implements RequestService {
        final Map<Long, ApprovalRequest> store = new TreeMap<>();

        @Override
        public List<ApprovalRequest> findByStatus(RequestStatus status) {
            return store.values().stream()
                    .filter(r -> status == null || r.status() == status)
                    .sorted(Comparator.comparing(ApprovalRequest::id))
                    .toList();
        }

        @Override
        public Optional<ApprovalRequest> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }
    }

    static final Employee HARU = new Employee(1L, "ハル", "haru@example.com");
    static final Employee SATO = new Employee(2L, "佐藤", "sato@example.com");
    static final Employee MOKA = new Employee(3L, "モカ", "moka@example.com");

    static LocalDateTime at(int day, int hour) {
        return LocalDateTime.of(2026, 10, day, hour, 30);
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        MemoryRequestService service = new MemoryRequestService();
        service.store.put(1L, new ApprovalRequest(1L, "出張交通費(大阪)", RequestType.EXPENSE, 12800,
                "大阪支店での打合せ", RequestStatus.SUBMITTED, HARU, null, null,
                at(1, 9), at(1, 10), null));
        service.store.put(2L, new ApprovalRequest(2L, "有給休暇(10/20)", RequestType.LEAVE, null,
                "私用のため", RequestStatus.SUBMITTED, SATO, null, null,
                at(2, 9), at(2, 9), null));
        service.store.put(3L, new ApprovalRequest(3L, "モニター購入", RequestType.PURCHASE, 45000,
                "在宅勤務用", RequestStatus.APPROVED, HARU, MOKA, "OKです",
                at(3, 9), at(3, 11), at(4, 15)));
        service.store.put(4L, new ApprovalRequest(4L, "書籍購入", RequestType.PURCHASE, 3200,
                "Java の本", RequestStatus.DRAFT, SATO, null, null,
                at(5, 9), null, null));

        // Spring Boot と同じく、日時を ISO-8601 の文字列で出す ObjectMapper にしておく
        var mapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mvc = MockMvcBuilders.standaloneSetup(new RequestApiController(service))
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter(mapper))
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    private MockHttpServletResponse call(String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse();
    }

    @Test
    @DisplayName("GET /api/requests?status=SUBMITTED で申請中の2件が一覧用の項目で返る")
    void listSubmitted() throws Exception {
        MockHttpServletResponse res = call("/api/requests?status=SUBMITTED");
        assertEquals(200, res.getStatus(),
                "GET /api/requests は 200 のはずです。list に @GetMapping と @RequestParam(required = false) を付けましたか?");
        mvc.perform(get("/api/requests?status=SUBMITTED"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("出張交通費(大阪)"))
                .andExpect(jsonPath("$[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$[0].amount").value(12800))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].applicantName").value("ハル"))
                .andExpect(jsonPath("$[0].submittedAt").value("2026-10-01T10:30:00"))
                .andExpect(jsonPath("$[1].title").value("有給休暇(10/20)"))
                .andExpect(jsonPath("$[1].amount").value(nullValue()));
    }

    @Test
    @DisplayName("status を省略すると全件、APPROVED なら承認済の1件だけ")
    void listAllAndApproved() throws Exception {
        assertEquals(200, call("/api/requests").getStatus(),
                "status を付けない GET /api/requests も 200 のはずです(@RequestParam(required = false))");
        mvc.perform(get("/api/requests"))
                .andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/requests?status=APPROVED"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("モニター購入"));
    }

    @Test
    @DisplayName("一覧は軽く:理由(reason)やコメントは一覧には出さない")
    void summaryIsLight() throws Exception {
        assertEquals(200, call("/api/requests").getStatus(), "GET /api/requests は 200 のはずです");
        mvc.perform(get("/api/requests"))
                .andExpect(jsonPath("$[0].reason").doesNotExist())
                .andExpect(jsonPath("$[0].comment").doesNotExist())
                .andExpect(jsonPath("$[0].applicant").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/requests/3 で承認済の詳細(承認者名・コメント・判断日時)が返る")
    void detailApproved() throws Exception {
        assertEquals(200, call("/api/requests/3").getStatus(),
                "存在する申請の GET /api/requests/{id} は 200 のはずです。@GetMapping(\"/{id}\") と @PathVariable を付けましたか?");
        mvc.perform(get("/api/requests/3"))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.type").value("PURCHASE"))
                .andExpect(jsonPath("$.reason").value("在宅勤務用"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.applicantName").value("ハル"))
                .andExpect(jsonPath("$.approverName").value("モカ"))
                .andExpect(jsonPath("$.comment").value("OKです"))
                .andExpect(jsonPath("$.decidedAt").value("2026-10-04T15:30:00"));
    }

    @Test
    @DisplayName("未承認の詳細は approverName が null。メールアドレスはどこにも出さない")
    void detailHidesEmail() throws Exception {
        MockHttpServletResponse res = call("/api/requests/1");
        assertEquals(200, res.getStatus(), "GET /api/requests/1 は 200 のはずです");
        mvc.perform(get("/api/requests/1"))
                .andExpect(jsonPath("$.approverName").value(nullValue()))
                .andExpect(jsonPath("$.applicantName").value("ハル"));
        assertFalse(res.getContentAsString().contains("@example.com"),
                "JSON にメールアドレスが含まれています。エンティティ(Employee)ではなく名前だけを DTO に入れてください: "
                        + res.getContentAsString());
    }

    @Test
    @DisplayName("存在しない申請 GET /api/requests/99 は 404")
    void notFound() throws Exception {
        assertEquals(200, call("/api/requests/1").getStatus(),
                "まず存在する申請の GET /api/requests/1 が 200 になるようにしてください");
        assertEquals(404, call("/api/requests/99").getStatus(),
                "存在しない申請は 404 Not Found にします(ResponseEntity.of(Optional) か notFound())");
    }
}
