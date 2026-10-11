import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** テストの「準備」をまとめたクラス */
public class OrderTestSupport {

    /** 渡された Service(スタブ)を差し込んだ OrderController の MockMvc を組み立てる */
    static MockMvc mockMvc(OrderService service) {
        // TODO: MockMvcBuilders.standaloneSetup(new OrderController(service)) で組み立てる
        // TODO: 日本語が文字化けしないように、StringHttpMessageConverter(UTF-8) と
        //       MappingJackson2HttpMessageConverter を setMessageConverters で渡し、
        //       defaultResponseCharacterEncoding(UTF-8) も指定して build() する
        return null; // ← ダミー。書き換えてください
    }
}

/** テスト用の手書きスタブ。Mockito の when/thenReturn と verify を手で書いたもの */
class StubOrderService implements OrderService {

    private final Map<String, List<Order>> data = new HashMap<>();
    /** findByCustomer が呼ばれたときの引数を、呼ばれた順に記録する */
    final List<String> calledWith = new ArrayList<>();

    /** customerId で呼ばれたら orders を返すように仕込む(when(...).thenReturn(...) に相当) */
    StubOrderService willReturn(String customerId, Order... orders) {
        // TODO: data に customerId → List.of(orders) を登録する
        return this;
    }

    @Override
    public List<Order> findByCustomer(String customerId) {
        // TODO: 呼ばれた引数を calledWith に記録する(verify の代わり)
        // TODO: 仕込んだ注文を返す。仕込んでいない顧客は空リスト(null は NG)
        return null; // ← ダミー。書き換えてください
    }
}

// ---- ここから下はテスト対象(完成済み。変更しない) ----

/** 注文を探す Service。本物は Repository 経由で DB を見る */
interface OrderService {
    List<Order> findByCustomer(String customerId);
}

record Order(String id, String item, int amount) { }

@RestController
class OrderController {

    private final OrderService service;

    OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping("/customers/{id}/orders")
    public List<Order> list(@PathVariable("id") String id) {
        return service.findByCustomer(id);
    }

    @GetMapping("/customers/{id}/orders/total")
    public String total(@PathVariable("id") String id) {
        List<Order> orders = service.findByCustomer(id);
        int sum = orders.stream().mapToInt(Order::amount).sum();
        return String.format(Locale.JAPAN, "合計 %,d円(%d件)", sum, orders.size());
    }
}
