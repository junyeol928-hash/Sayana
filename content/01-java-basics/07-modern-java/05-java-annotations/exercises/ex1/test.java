import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OwnerReportTest {

    static class OrderService {
        @Owner(value = "高橋", team = "決済")
        public void place() { }

        @Owner("伊藤")
        public void cancel() { }

        @Owner(value = "高橋", team = "決済")
        void audit() { }

        public void health() { }

        private void helper() { }
    }

    static class NoOwners {
        public void a() { }

        public void b() { }
    }

    @Test
    @DisplayName("@Owner は実行時まで残る(RUNTIME)")
    void retentionIsRuntime() {
        Retention r = Owner.class.getAnnotation(Retention.class);
        assertNotNull(r, "@Owner に @Retention が付いていません");
        assertEquals(RetentionPolicy.RUNTIME, r.value(),
                "@Owner の @Retention を RUNTIME にしてください。SOURCE や CLASS のままだと getAnnotation が常に null を返します");
    }

    @Test
    @DisplayName("担当者一覧:メソッド名の昇順で「名前: 担当者(チーム)」")
    void owners() {
        assertEquals(List.of("audit: 高橋(決済)", "cancel: 伊藤(未所属)", "place: 高橋(決済)"),
                OwnerReport.owners(OrderService.class),
                "@Owner が付いた3つのメソッドを名前順に並べてください。team を省略したものは既定値の「未所属」です。"
                        + "public でない audit も getDeclaredMethods で取れます");
    }

    @Test
    @DisplayName("雛形の PaymentService でも問題文の例どおりになる")
    void ownersOfPaymentService() {
        assertEquals(List.of("charge: 佐藤(未所属)", "refund: 鈴木(経理)"),
                OwnerReport.owners(PaymentService.class),
                "区切りは「: 」(コロン+半角スペース)、チームは全角のかっこ()で囲みます");
    }

    @Test
    @DisplayName("担当者がいないメソッドを名前順で返す")
    void unowned() {
        assertEquals(List.of("health", "helper"), OwnerReport.unowned(OrderService.class),
                "@Owner が付いていないのは health と helper です(private でも対象)。名前の昇順で返してください");
        assertEquals(List.of("ping"), OwnerReport.unowned(PaymentService.class),
                "PaymentService で @Owner が付いていないのは ping だけです");
    }

    @Test
    @DisplayName("1つも付いていないクラス")
    void noOwners() {
        assertEquals(List.of(), OwnerReport.owners(NoOwners.class),
                "@Owner が1つも付いていないクラスなら、担当者一覧は空のリストです");
        assertEquals(List.of("a", "b"), OwnerReport.unowned(NoOwners.class),
                "@Owner が1つも付いていないクラスなら、全メソッドの名前が名前順に並びます");
    }
}
