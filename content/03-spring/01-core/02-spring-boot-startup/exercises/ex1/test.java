import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class InvoiceAutoConfigTest {

    @Test
    @DisplayName("利用者の設定なし:既定の 10% で 1000円 → 1100円")
    void defaultRate() {
        try (var ctx = new AnnotationConfigApplicationContext(InvoiceAutoConfig.class)) {
            InvoiceService service = ctx.getBean(InvoiceService.class);
            assertInstanceOf(StandardTaxRate.class, service.taxRate(),
                    "TaxRate の Bean が無いときは StandardTaxRate を使ってください");
            assertEquals(1100, service.total(1000), "1000円の税込(10%)は1100円です。total の計算を確認してください");
        }
    }

    @Test
    @DisplayName("利用者が TaxRate を定義したら、そちらが使われる(1000円 → 1080円)")
    void userRateWins() {
        try (var ctx = new AnnotationConfigApplicationContext(ReducedTaxConfig.class, InvoiceAutoConfig.class)) {
            InvoiceService service = ctx.getBean(InvoiceService.class);
            assertEquals(8, service.taxRate().percent(),
                    "利用者が定義した TaxRate(8%)を使ってください。ObjectProvider の getIfAvailable で取り出せます");
            assertEquals(1080, service.total(1000), "8% なら 1000円 → 1080円です");
        }
    }

    @Test
    @DisplayName("1円未満は切り捨て:999円 → 1098円、0円 → 0円(境界値)")
    void roundingAndZero() {
        try (var ctx = new AnnotationConfigApplicationContext(InvoiceAutoConfig.class)) {
            InvoiceService service = ctx.getBean(InvoiceService.class);
            assertEquals(1098, service.total(999), "999円の 10% は 99.9円 → 99円。int の割り算で切り捨ててください");
            assertEquals(0, service.total(0), "0円の税込は0円です");
        }
    }

    @Test
    @DisplayName("InvoiceService は Bean として1つだけ登録される")
    void singleInvoiceServiceBean() {
        try (var ctx = new AnnotationConfigApplicationContext(ReducedTaxConfig.class, InvoiceAutoConfig.class)) {
            assertEquals(1, ctx.getBeansOfType(InvoiceService.class).size(),
                    "invoiceService() に @Bean を付けて、InvoiceService を Bean として登録してください");
        }
    }
}
