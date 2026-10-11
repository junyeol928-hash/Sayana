import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

class GreetingControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new GreetingController())
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    private MockHttpServletResponse send(RequestBuilder req) throws Exception {
        return mvc.perform(req).andReturn().getResponse();
    }

    @Test
    @DisplayName("GET /hello?name=ハル で「こんにちは、ハルさん!」")
    void helloWithName() throws Exception {
        MockHttpServletResponse res = send(get("/hello").param("name", "ハル"));
        assertEquals(200, res.getStatus(),
                "GET /hello が 200 になりません。クラスに @RestController、メソッドに @GetMapping(\"/hello\") が付いているか確認してください");
        assertEquals("こんにちは、ハルさん!", res.getContentAsString(),
                "name=ハル のときは「こんにちは、ハルさん!」を返してください(! は全角です)");
    }

    @Test
    @DisplayName("name が無いときは「こんにちは、ゲストさん!」")
    void helloWithoutName() throws Exception {
        MockHttpServletResponse res = send(get("/hello"));
        assertEquals(200, res.getStatus(),
                "name なしで 400 になっていませんか? @RequestParam(defaultValue = \"ゲスト\") を使うと必須でなくなります");
        assertEquals("こんにちは、ゲストさん!", res.getContentAsString(),
                "name が無いときは ゲスト として扱ってください");
    }

    @Test
    @DisplayName("name が空文字(/hello?name=)のときもゲスト扱い")
    void helloWithEmptyName() throws Exception {
        MockHttpServletResponse res = send(get("/hello").param("name", ""));
        assertEquals("こんにちは、ゲストさん!", res.getContentAsString(),
                "?name= のように空のときもゲストにしてください。defaultValue は空文字のときにも使われます");
    }

    @Test
    @DisplayName("GET /members/42 で「会員番号: 42」")
    void member() throws Exception {
        MockHttpServletResponse res = send(get("/members/42"));
        assertEquals(200, res.getStatus(),
                "GET /members/42 が 200 になりません。@GetMapping(\"/members/{id}\") と @PathVariable を確認してください");
        assertEquals("会員番号: 42", res.getContentAsString(),
                "「会員番号: 42」の形で返してください(コロンは半角、後ろに半角スペース1つ)");
    }

    @Test
    @DisplayName("GET /members/abc は数値に変換できないので 400")
    void memberNotNumber() throws Exception {
        assertEquals(400, send(get("/members/abc")).getStatus(),
                "id を int で受け取れば、数値でないときは Spring が自動で 400 を返します。id の型を確認してください");
    }

    @Test
    @DisplayName("POST /hello は 405(メソッド違い)")
    void postHelloIsNotAllowed() throws Exception {
        assertEquals(405, send(post("/hello")).getStatus(),
                "/hello は GET 専用です。@RequestMapping ではなく @GetMapping を使ってください(POST は 405 になるはず)");
    }
}
