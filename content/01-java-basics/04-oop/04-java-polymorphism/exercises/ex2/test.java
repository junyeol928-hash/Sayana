import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckoutTest {

    @Test
    @DisplayName("現金は手数料 0 円")
    void cash() {
        Payment p = new CashPayment();
        assertEquals(5000, Checkout.total(p, 5000),
                "現金払いは手数料 0 円なので、支払総額は金額そのままです");
    }

    @Test
    @DisplayName("カードは 3%(切り捨て)")
    void card() {
        Payment p = new CardPayment();
        assertEquals(150, p.fee(5000),
                "カードの手数料は金額の 3% です(5000円 → 150円)。CardPayment で fee をオーバーライドしましたか?");
        assertEquals(5150, Checkout.total(p, 5000),
                "支払総額は 金額 + 手数料 です。Checkout.total で p.fee(amount) を足していますか?");
        assertEquals(101, p.fee(3399),
                "3399円の 3% は 101.97 → 切り捨てて 101円です");
    }

    @Test
    @DisplayName("カードの手数料は最低 100 円(境界値)")
    void cardMinimum() {
        Payment p = new CardPayment();
        assertEquals(100, p.fee(3000),
                "3000円の 3% は 90円ですが、最低手数料 100円が適用されます");
        assertEquals(100, p.fee(3333),
                "3333円の 3% は 99円なので、最低手数料 100円が適用されます");
        assertEquals(100, p.fee(3334),
                "3334円の 3% は 100.02 → 切り捨てて 100円です");
    }

    @Test
    @DisplayName("ポイントは 1000 円未満だけ 50 円(境界値)")
    void point() {
        Payment p = new PointPayment();
        assertEquals(50, p.fee(999),
                "ポイント払いで 1000円未満なら手数料は 50円です");
        assertEquals(0, p.fee(1000),
                "ポイント払いで 1000円ちょうどなら手数料は 0円です(「未満」の判定を確認してください)");
    }

    @Test
    @DisplayName("receipt() は支払い方法に応じた明細を返す")
    void receipt() {
        assertEquals("カード払い: 5000円 + 手数料150円 = 5150円",
                Checkout.receipt(new CardPayment(), 5000),
                "明細の形式は \"カード払い: 5000円 + 手数料150円 = 5150円\" です。p.label() と p.fee(amount) を使ってください");
        assertEquals("ポイント払い: 800円 + 手数料50円 = 850円",
                Checkout.receipt(new PointPayment(), 800),
                "ポイント払い 800円の明細は \"ポイント払い: 800円 + 手数料50円 = 850円\" です");
        assertEquals("現金払い: 800円 + 手数料0円 = 800円",
                Checkout.receipt(new CashPayment(), 800),
                "現金払いの label() は \"現金\" です。CashPayment で label をオーバーライドしましたか?");
    }

    @Test
    @DisplayName("Checkout は新しい支払い方法も、種類を判定せずに扱える")
    void newPaymentType() {
        Payment eMoney = new Payment() {
            @Override
            String label() {
                return "電子マネー";
            }

            @Override
            int fee(int amount) {
                return 10;
            }
        };
        assertEquals(1010, Checkout.total(eMoney, 1000),
                "知らない支払い方法でも p.fee(amount) を呼べば正しく計算できるはずです。instanceof で種類を判定していませんか?");
    }
}
