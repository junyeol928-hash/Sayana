import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderTestSupportTest {

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

    // テストデータ:意図が読める最小限の注文を、名前付きのメソッドで作る
    private static StubOrderService twoOrders() {
        return new StubOrderService().willReturn("C001",
                new Order("O-1", "ボールペン", 300),
                new Order("O-2", "ノート", 1200));
    }

    private static MockMvc build(OrderService service) {
        MockMvc mvc = OrderTestSupport.mockMvc(service);
        assertNotNull(mvc, "OrderTestSupport.mockMvc が null を返しています。MockMvcBuilders.standaloneSetup(...).build() の結果を返してください");
        return mvc;
    }

    @Test
    @DisplayName("スタブ:仕込んだ注文を返し、仕込んでいない顧客には空リストを返す")
    void stubReturns() {
        StubOrderService stub = twoOrders();
        List<Order> found = stub.findByCustomer("C001");
        assertNotNull(found, "findByCustomer が null を返しています。仕込んだ注文のリストを返してください");
        assertEquals(2, found.size(), "willReturn で仕込んだ2件が返るはずです。data に登録していますか?");
        assertEquals("ノート", found.get(1).item(), "仕込んだ順に返してください");
        assertEquals(List.of(), stub.findByCustomer("C999"),
                "仕込んでいない顧客には null ではなく空リストを返してください(getOrDefault が便利)");
    }

    @Test
    @DisplayName("スタブ:呼ばれた引数を順に記録する(verify の代わり)")
    void stubRecordsCalls() {
        StubOrderService stub = twoOrders();
        stub.findByCustomer("C001");
        stub.findByCustomer("C777");
        assertEquals(List.of("C001", "C777"), stub.calledWith,
                "findByCustomer の引数を calledWith に呼ばれた順で add してください");
    }

    @Test
    @DisplayName("組み立てた MockMvc で、注文一覧が JSON で返る")
    void listAsJson() throws Exception {
        StubOrderService stub = twoOrders();
        MockMvc mvc = build(stub);
        hint("GET /customers/C001/orders で、スタブに仕込んだ2件が JSON で返るはずです",
                () -> mvc.perform(get("/customers/C001/orders"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(2))
                        .andExpect(jsonPath("$[0].item").value("ボールペン"))
                        .andExpect(jsonPath("$[1].amount").value(1200)));
        assertEquals(List.of("C001"), stub.calledWith,
                "Controller から findByCustomer(\"C001\") が1回呼ばれたことを、スタブが記録しているはずです");
    }

    @Test
    @DisplayName("日本語の本文が文字化けしない(UTF-8 の設定)")
    void japaneseText() throws Exception {
        MockMvc mvc = build(twoOrders());
        hint("「合計 1,500円(2件)」が文字化けしています。setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8), ...) と defaultResponseCharacterEncoding(StandardCharsets.UTF_8) を指定してください",
                () -> mvc.perform(get("/customers/C001/orders/total"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith("text/plain"))
                        .andExpect(content().encoding("UTF-8"))
                        .andExpect(content().string("合計 1,500円(2件)")));
    }

    @Test
    @DisplayName("注文の無い顧客は空配列と「合計 0円(0件)」")
    void emptyCustomer() throws Exception {
        MockMvc mvc = build(twoOrders());
        hint("注文の無い顧客の一覧は空の JSON 配列 [] です",
                () -> mvc.perform(get("/customers/C999/orders"))
                        .andExpect(status().isOk())
                        .andExpect(content().json("[]")));
        hint("注文の無い顧客の合計は「合計 0円(0件)」です",
                () -> mvc.perform(get("/customers/C999/orders/total"))
                        .andExpect(content().string("合計 0円(0件)")));
    }

    @Test
    @DisplayName("ラムダで書いたスタブも差し込める(メソッド1つのインターフェースだから)")
    void lambdaStub() throws Exception {
        MockMvc mvc = build(id -> List.of(new Order("O-9", id + "様の特注品", 5000)));
        hint("ラムダのスタブを差し込んだ MockMvc でも動くはずです。mockMvc の引数の service を OrderController に渡していますか?",
                () -> mvc.perform(get("/customers/C042/orders"))
                        .andExpect(jsonPath("$[0].item").value("C042様の特注品")));
    }
}
