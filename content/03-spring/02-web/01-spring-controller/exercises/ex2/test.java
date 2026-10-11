import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

import com.jayway.jsonpath.JsonPath;

class StockApiControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StockApiController())
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    private MockHttpServletResponse send(RequestBuilder req) throws Exception {
        return mvc.perform(req).andReturn().getResponse();
    }

    private int quantityOf(String code) throws Exception {
        MockHttpServletResponse res = send(get("/api/stocks/" + code));
        assertEquals(200, res.getStatus(), "GET /api/stocks/" + code + " が 200 になりません");
        Integer q = JsonPath.read(res.getContentAsString(), "$.quantity");
        return q;
    }

    @Test
    @DisplayName("GET /api/stocks/A001 は 200 で在庫の JSON を返す")
    void findExisting() throws Exception {
        MockHttpServletResponse res = send(get("/api/stocks/A001"));
        assertEquals(200, res.getStatus(),
                "GET /api/stocks/A001 が 200 になりません。@RestController・@RequestMapping(\"/api/stocks\")・@GetMapping(\"/{code}\") を確認してください");
        String json = res.getContentAsString();
        assertEquals("A001", JsonPath.read(json, "$.code"), "JSON の code が違います");
        assertEquals("ボールペン", JsonPath.read(json, "$.name"), "JSON の name が違います");
        assertEquals(Integer.valueOf(12), JsonPath.<Integer>read(json, "$.quantity"), "JSON の quantity が違います");
    }

    @Test
    @DisplayName("存在しない商品の照会は 404")
    void findMissing() throws Exception {
        assertEquals(404, send(get("/api/stocks/Z999")).getStatus(),
                "無い商品は ResponseEntity.notFound().build() で 404 を返してください(null を返していませんか?)");
    }

    @Test
    @DisplayName("A001 を 5 個出荷すると 200 で残り 7、照会しても 7")
    void shipOk() throws Exception {
        MockHttpServletResponse res = send(post("/api/stocks/A001/ship").param("qty", "5"));
        assertEquals(200, res.getStatus(),
                "出荷できるときは 200 を返してください。@PostMapping(\"/{code}/ship\") と @RequestParam int qty を確認してください");
        assertEquals(Integer.valueOf(7), JsonPath.<Integer>read(res.getContentAsString(), "$.quantity"),
                "出荷後の Stock(12 - 5 = 7)を返してください");
        assertEquals(7, quantityOf("A001"), "出荷後の在庫を stocks に入れ直していますか?(照会しても 7 になるはず)");
    }

    @Test
    @DisplayName("在庫ちょうど(C003 を 5 個)は出荷できて残り 0")
    void shipExactly() throws Exception {
        MockHttpServletResponse res = send(post("/api/stocks/C003/ship").param("qty", "5"));
        assertEquals(200, res.getStatus(), "在庫 5 個から 5 個は出荷できるはずです。在庫不足の判定が < ではなく <= になっていませんか?");
        assertEquals(0, quantityOf("C003"), "出荷後の在庫は 0 になるはずです");
    }

    @Test
    @DisplayName("在庫不足(A001 を 13 個、B002 を 1 個)は 409 で在庫は減らない")
    void shipConflict() throws Exception {
        assertEquals(409, send(post("/api/stocks/A001/ship").param("qty", "13")).getStatus(),
                "在庫 12 個に 13 個の出荷は ResponseEntity.status(HttpStatus.CONFLICT).build() で 409 を返してください");
        assertEquals(12, quantityOf("A001"), "在庫不足のときは在庫を減らさないでください");
        assertEquals(409, send(post("/api/stocks/B002/ship").param("qty", "1")).getStatus(),
                "在庫 0 個の商品の出荷も 409 です");
    }

    @Test
    @DisplayName("qty が 0 以下・qty なしは 400、無い商品の出荷は 404")
    void shipBadRequest() throws Exception {
        assertEquals(400, send(post("/api/stocks/A001/ship").param("qty", "0")).getStatus(),
                "qty が 0 以下なら ResponseEntity.badRequest().build() で 400 を返してください");
        assertEquals(400, send(post("/api/stocks/A001/ship")).getStatus(),
                "qty なしは @RequestParam(必須)によって自動で 400 になるはずです。required = false にしていませんか?");
        assertEquals(12, quantityOf("A001"), "400 のときは在庫を変えないでください");
        assertEquals(404, send(post("/api/stocks/Z999/ship").param("qty", "1")).getStatus(),
                "無い商品の出荷は 404 を返してください");
    }
}
