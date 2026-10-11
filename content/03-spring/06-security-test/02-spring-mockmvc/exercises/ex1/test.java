import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockControllerTest {

    private MockMvc mvc;

    /** MockMvc の検証失敗(英語のメッセージ)に、日本語で「何を直せばよいか」を添える */
    interface MvcCheck {
        void run() throws Exception;
    }

    private static void hint(String message, MvcCheck check) throws Exception {
        try {
            check.run();
        } catch (AssertionError e) {
            throw new AssertionError(message + "(詳細: " + e.getMessage() + ")", e);
        }
    }

    @BeforeEach
    void setUp() {
        // テストごとに新しい Controller を作る(在庫の状態がテスト間で混ざらないように)
        mvc = MockMvcBuilders.standaloneSetup(new StockController())
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("GET /stocks/A001 は 200 で在庫を JSON で返す")
    void findFound() throws Exception {
        hint("GET /stocks/A001 が 200 + JSON になっていません。find に @GetMapping(\"/{code}\") を付け、ResponseEntity.ok(stock) を返していますか?",
                () -> mvc.perform(get("/stocks/A001"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value("A001"))
                        .andExpect(jsonPath("$.name").value("ボールペン"))
                        .andExpect(jsonPath("$.quantity").value(12)));
    }

    @Test
    @DisplayName("存在しない商品コードは 404(GET も POST も)")
    void notFound() throws Exception {
        hint("GET で存在しない商品コードなら ResponseEntity.notFound().build() で 404 を返してください",
                () -> mvc.perform(get("/stocks/Z999")).andExpect(status().isNotFound()));
        hint("POST /stocks/Z999/ship でも、商品が無ければ 404 です(数量チェックより先に)",
                () -> mvc.perform(post("/stocks/Z999/ship").param("qty", "1"))
                        .andExpect(status().isNotFound()));
    }

    @Test
    @DisplayName("出荷すると在庫が減り、GET で確かめられる")
    void ship() throws Exception {
        hint("POST /stocks/A001/ship?qty=3 は 200 +「出荷しました(残り 9)」です。ship に @PostMapping(\"/{code}/ship\") を付けていますか?",
                () -> mvc.perform(post("/stocks/A001/ship").param("qty", "3"))
                        .andExpect(status().isOk())
                        .andExpect(content().string("出荷しました(残り 9)")));
        hint("出荷後に GET すると数量が 9 に減っているはずです。数量を減らした新しい Stock を stocks.put していますか?",
                () -> mvc.perform(get("/stocks/A001"))
                        .andExpect(jsonPath("$.quantity").value(9)));
    }

    @Test
    @DisplayName("在庫ちょうどは出荷でき、1つでも超えたら 409(境界値)")
    void boundary() throws Exception {
        hint("在庫 12 個に 13 個の出荷は 409 +「在庫不足です(残り 12)」です",
                () -> mvc.perform(post("/stocks/A001/ship").param("qty", "13"))
                        .andExpect(status().isConflict())
                        .andExpect(content().string("在庫不足です(残り 12)")));
        hint("在庫 12 個に 12 個ちょうどの出荷はできます(残り 0)。qty >= 在庫 で不足扱いにしていませんか?不足なのは qty > 在庫 のときだけです",
                () -> mvc.perform(post("/stocks/A001/ship").param("qty", "12"))
                        .andExpect(status().isOk())
                        .andExpect(content().string("出荷しました(残り 0)")));
        hint("在庫 0 個のときに 1 個出荷しようとしたら 409 +「在庫不足です(残り 0)」です",
                () -> mvc.perform(post("/stocks/A001/ship").param("qty", "1"))
                        .andExpect(status().isConflict())
                        .andExpect(content().string("在庫不足です(残り 0)")));
    }

    @Test
    @DisplayName("数量が 0 以下なら 400")
    void badQuantity() throws Exception {
        hint("qty=0 は 400 +「数量は1以上で指定してください」です",
                () -> mvc.perform(post("/stocks/A001/ship").param("qty", "0"))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().string("数量は1以上で指定してください")));
        hint("qty がマイナスでも 400 です",
                () -> mvc.perform(post("/stocks/A001/ship").param("qty", "-1"))
                        .andExpect(status().isBadRequest()));
    }
}
