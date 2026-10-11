import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.SerializationFeature;

class TaskApiTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Spring Boot と同じく、日時を ISO-8601 の文字列で出す ObjectMapper にしておく
        var mapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mvc = MockMvcBuilders.standaloneSetup(new TaskApi())
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter(mapper))
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    private MockHttpServletResponse doGet(String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse();
    }

    private MockHttpServletResponse doPatch(String url, String json) throws Exception {
        return mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse();
    }

    @Test
    @DisplayName("GET は DTO の4項目を返し、期限は \"2026-10-31\" 形式")
    void getReturnsDto() throws Exception {
        MockHttpServletResponse res = doGet("/api/tasks/1");
        assertEquals(200, res.getStatus(),
                "GET /api/tasks/1 が 200 になりません。TaskResponse.from が null を返すと Optional が空になって 404 になります");
        mvc.perform(get("/api/tasks/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("見積書を作る"))
                .andExpect(jsonPath("$.done").value(false))
                .andExpect(jsonPath("$.dueDate").value("2026-10-31"));
    }

    @Test
    @DisplayName("社内メモ internalMemo は JSON に含めない")
    void memoIsHidden() throws Exception {
        MockHttpServletResponse res = doGet("/api/tasks/1");
        assertEquals(200, res.getStatus(), "GET /api/tasks/1 が 200 になりません。先に TaskResponse.from を完成させてください");
        assertFalse(res.getContentAsString().contains("値引き"),
                "社内メモが JSON に漏れています。TaskResponse に internalMemo を入れないでください");
        mvc.perform(get("/api/tasks/1")).andExpect(jsonPath("$.internalMemo").doesNotExist());
    }

    @Test
    @DisplayName("PATCH {\"done\":true} は done だけ変え、title と期限はそのまま")
    void patchDoneOnly() throws Exception {
        MockHttpServletResponse res = doPatch("/api/tasks/1", "{\"done\":true}");
        assertEquals(200, res.getStatus(), "PATCH が 200 になりません");
        assertFalse(res.getContentAsString().isEmpty(), "PATCH の本文が空です。更新後の TaskResponse を返してください");
        mvc.perform(get("/api/tasks/1"))
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.title").value("見積書を作る"))
                .andExpect(jsonPath("$.dueDate").value("2026-10-31"));
    }

    @Test
    @DisplayName("PATCH {\"dueDate\":...} は期限だけ変え、先に true にした done は true のまま")
    void patchDueDateOnly() throws Exception {
        doPatch("/api/tasks/2", "{\"done\":true}");
        MockHttpServletResponse res = doPatch("/api/tasks/2", "{\"dueDate\":\"2026-11-15\"}");
        assertEquals(200, res.getStatus(), "PATCH が 200 になりません");
        mvc.perform(get("/api/tasks/2"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-15"))
                .andExpect(jsonPath("$.title").value("請求書を送る"));
        MockHttpServletResponse after = doGet("/api/tasks/2");
        assertTrue(after.getContentAsString().contains("\"done\":true"),
                "dueDate だけ送ったのに done が false に戻っています。null の項目は「変更しない」にしてください"
                        + "(boolean ではなく Boolean で受け取っている意味を思い出して)");
    }

    @Test
    @DisplayName("PATCH の返り値も更新後の DTO")
    void patchReturnsUpdated() throws Exception {
        mvc.perform(patch("/api/tasks/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"見積書を作って送る\"}"))
                .andExpect(jsonPath("$.title").value("見積書を作って送る"))
                .andExpect(jsonPath("$.dueDate").value("2026-10-31"))
                .andExpect(jsonPath("$.internalMemo").doesNotExist());
    }

    @Test
    @DisplayName("存在しない ID への PATCH は 404")
    void patchNotFound() throws Exception {
        assertEquals(404, doPatch("/api/tasks/99", "{\"done\":true}").getStatus(),
                "存在しない ID なら 404 を返してください");
    }
}
