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
        return MockMvcBuilders.standaloneSetup(new OrderController(service))
                // standaloneSetup の既定は ISO-8859-1。日本語を返すので UTF-8 にする
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }
}

/** テスト用の手書きスタブ。Mockito の when/thenReturn と verify を手で書いたもの */
class StubOrderService implements OrderService {

    private final Map<String, List<Order>> data = new HashMap<>();
    /** findByCustomer が呼ばれたときの引数を、呼ばれた順に記録する */
    final List<String> calledWith = new ArrayList<>();

    /** customerId で呼ばれたら orders を返すように仕込む(when(...).thenReturn(...) に相当) */
    StubOrderService willReturn(String customerId, Order... orders) {
        data.put(customerId, List.of(orders));
        return this;
    }

    @Override
    public List<Order> findByCustomer(String customerId) {
        calledWith.add(customerId);
        // 仕込んでいない顧客は空リスト(null を返すと Controller が NullPointerException になる)
        return data.getOrDefault(customerId, List.of());
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
