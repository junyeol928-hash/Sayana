import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckoutServiceTest {

    private AnnotationConfigApplicationContext ctx;

    @BeforeEach
    void setUp() {
        // @Primary や @Qualifier が無いと、ここで NoUniqueBeanDefinitionException になる
        ctx = new AnnotationConfigApplicationContext(
                CardPayment.class, BankTransfer.class, KonbiniPayment.class, CheckoutService.class);
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    @DisplayName("Bean 名が card / bank / konbini になっている")
    void beanNames() {
        assertTrue(ctx.containsBean("card"), "CardPayment に @Component(\"card\") で Bean 名を付けてください");
        assertTrue(ctx.containsBean("bank"), "BankTransfer に @Component(\"bank\") で Bean 名を付けてください");
        assertTrue(ctx.containsBean("konbini"), "KonbiniPayment に @Component(\"konbini\") で Bean 名を付けてください");
    }

    @Test
    @DisplayName("型だけで取り出すと @Primary のカード決済になり、10000円 → 10300円")
    void primaryIsCard() {
        assertInstanceOf(CardPayment.class, ctx.getBean(PaymentGateway.class),
                "CardPayment に @Primary を付けてください");
        assertEquals(10300, ctx.getBean(CheckoutService.class).totalWithDefault(10000),
                "既定(カード)の手数料は 3% なので 10000円 → 10300円です");
    }

    @Test
    @DisplayName("@Qualifier(\"bank\") で銀行振込が選ばれ、10000円 → 10440円")
    void qualifierBank() {
        assertEquals(10440, ctx.getBean(CheckoutService.class).totalByBank(10000),
                "コンストラクタの2番目の引数に @Qualifier(\"bank\") を付け、手数料 440円を足してください");
    }

    @Test
    @DisplayName("Bean 名で選ぶ:konbini は +220円、card の 999円は 1円未満切り捨てで 1028円")
    void totalByName() {
        CheckoutService service = ctx.getBean(CheckoutService.class);
        assertEquals(5220, service.totalBy("konbini", 5000), "konbini の手数料は一律 220円です");
        assertEquals(1028, service.totalBy("card", 999),
                "999円の 3% は 29.97円 → 切り捨てて 29円。合計 1028円です");
        assertEquals(1440, service.totalBy("bank", 1000), "bank の手数料は一律 440円です");
    }

    @Test
    @DisplayName("知らない決済手段は IllegalArgumentException")
    void unknownMethod() {
        CheckoutService service = ctx.getBean(CheckoutService.class);
        assertThrows(IllegalArgumentException.class, () -> service.totalBy("cash", 1000),
                "gateways に無い名前(cash)のときは IllegalArgumentException を投げてください");
    }
}
