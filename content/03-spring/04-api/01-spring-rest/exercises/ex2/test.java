import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.nio.charset.StandardCharsets;

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

class TaskControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TaskController())
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    private ResultActions postJson(String json) throws Exception {
        return mvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    @DisplayName("POST すると 201 Created と、作ったタスクの JSON が返る")
    void created() throws Exception {
        ResultActions r = postJson("{\"title\":\"請求書を送る\"}");
        MockHttpServletResponse res = r.andReturn().getResponse();
        assertEquals(201, res.getStatus(),
                "POST の成功は 201 Created です。ResponseEntity.created(...) を使っていますか?(今のステータス: "
                        + res.getStatus() + ")");
        r.andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("請求書を送る"))
                .andExpect(jsonPath("$.done").value(false));
    }

    @Test
    @DisplayName("Location ヘッダに /api/tasks/{id} が入る")
    void location() throws Exception {
        MockHttpServletResponse res = postJson("{\"title\":\"請求書を送る\"}").andReturn().getResponse();
        String location = res.getHeader("Location");
        assertNotNull(location, "Location ヘッダがありません。ResponseEntity.created(URI) に作ったタスクの URL を渡してください");
        assertTrue(location.endsWith("/api/tasks/1"),
                "1件目の Location は /api/tasks/1 で終わるはずです。実際: " + location);
    }

    @Test
    @DisplayName("2件目は ID が 2 になり、GET /api/tasks/2 で取り出せる")
    void secondTaskIsStored() throws Exception {
        postJson("{\"title\":\"請求書を送る\"}");
        MockHttpServletResponse res = postJson("{\"title\":\"会議室を予約する\"}").andReturn().getResponse();
        assertEquals(201, res.getStatus(), "2件目の POST も 201 になるはずです");
        String location = res.getHeader("Location");
        assertTrue(location != null && location.endsWith("/api/tasks/2"),
                "2件目の Location は /api/tasks/2 で終わるはずです。実際: " + location);

        MockHttpServletResponse got = mvc.perform(get("/api/tasks/2")).andReturn().getResponse();
        assertEquals(200, got.getStatus(), "POST したタスクが store に保存されていません(GET /api/tasks/2 が 404)");
        mvc.perform(get("/api/tasks/2"))
                .andExpect(jsonPath("$.title").value("会議室を予約する"));
    }

    @Test
    @DisplayName("title が空白だけなら 400 で、保存もされない")
    void blankTitle() throws Exception {
        MockHttpServletResponse res = postJson("{\"title\":\"   \"}").andReturn().getResponse();
        assertEquals(400, res.getStatus(), "title が空白だけのときは 400 Bad Request を返してください(isBlank() で判定)");
        assertEquals(404, mvc.perform(get("/api/tasks/1")).andReturn().getResponse().getStatus(),
                "400 を返したのにタスクが保存されています。チェックは採番・保存より前に行ってください");
    }

    @Test
    @DisplayName("title がない JSON({})も 400")
    void missingTitle() throws Exception {
        MockHttpServletResponse res = postJson("{}").andReturn().getResponse();
        assertEquals(400, res.getStatus(),
                "title が無い JSON では req.title() が null になります。null のときも 400 を返してください");
    }
}
