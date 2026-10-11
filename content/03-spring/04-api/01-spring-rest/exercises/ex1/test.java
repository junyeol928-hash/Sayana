import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InventoryControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new InventoryController())
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    private MockHttpServletResponse call(String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse();
    }

    @Test
    @DisplayName("GET /api/items で 200 と全3件の JSON 配列が返る")
    void listAll() throws Exception {
        MockHttpServletResponse res = call("/api/items");
        assertEquals(200, res.getStatus(),
                "GET /api/items が 200 になりません。list メソッドに @GetMapping を付けましたか?");
        String type = res.getContentType();
        assertNotNull(type, "Content-Type が空です。JSON を返していますか?");
        assertTrue(type.startsWith("application/json"), "Content-Type が application/json ではありません: " + type);
        mvc.perform(get("/api/items"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].code").value("A-001"))
                .andExpect(jsonPath("$[0].name").value("ボールペン"))
                .andExpect(jsonPath("$[1].stock").value(0));
    }

    @Test
    @DisplayName("GET /api/items?inStock=true なら在庫0のノート(A-002)が除かれる")
    void listInStock() throws Exception {
        MockHttpServletResponse res = call("/api/items?inStock=true");
        assertEquals(200, res.getStatus(), "GET /api/items?inStock=true が 200 になりません");
        mvc.perform(get("/api/items?inStock=true"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("A-001"))
                .andExpect(jsonPath("$[1].code").value("B-010"));
    }

    @Test
    @DisplayName("inStock を省略したときは false 扱い(全件)")
    void inStockDefault() throws Exception {
        assertEquals(200, call("/api/items").getStatus(),
                "inStock を付けないと 400 になっていませんか? @RequestParam(defaultValue = \"false\") にしてください");
        mvc.perform(get("/api/items")).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("GET /api/items/B-010 で 200 とその商品の JSON が返る")
    void findOne() throws Exception {
        MockHttpServletResponse res = call("/api/items/B-010");
        assertEquals(200, res.getStatus(),
                "GET /api/items/B-010 が 200 になりません。find に @GetMapping(\"/{code}\") と @PathVariable を付けましたか?");
        mvc.perform(get("/api/items/B-010"))
                .andExpect(jsonPath("$.code").value("B-010"))
                .andExpect(jsonPath("$.name").value("クリアファイル"))
                .andExpect(jsonPath("$.stock").value(35));
    }

    @Test
    @DisplayName("存在しない商品コードは 404")
    void notFound() throws Exception {
        assertEquals(200, call("/api/items/A-001").getStatus(),
                "まず存在する商品(A-001)が 200 になるようにしてください");
        assertEquals(404, call("/api/items/Z-999").getStatus(),
                "存在しない商品コードでは 404 を返してください(ResponseEntity.notFound().build())");
    }
}
