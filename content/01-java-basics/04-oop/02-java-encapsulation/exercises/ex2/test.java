import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    @DisplayName("フィールドはすべて private final")
    void fieldsArePrivateFinal() {
        for (Field f : Money.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m)) {
                continue;
            }
            assertTrue(Modifier.isPrivate(m) && Modifier.isFinal(m),
                    "フィールド " + f.getName() + " に private final が付いていません");
        }
    }

    @Test
    @DisplayName("正しい値で作れ、toString は「1200 JPY」の形式。金額 0 も OK")
    void createAndToString() {
        Money m = new Money(1200, "JPY");
        assertEquals(1200, m.getAmount(), "getAmount() はコンストラクタで渡した金額を返すはずです");
        assertEquals("JPY", m.getCurrency(), "getCurrency() はコンストラクタで渡した通貨を返すはずです");
        assertEquals("1200 JPY", m.toString(), "toString は「金額 半角スペース 通貨」の形式にしてください");
        assertEquals(0, new Money(0, "JPY").getAmount(), "金額 0 は正しい値なので作れるはずです");
    }

    @Test
    @DisplayName("マイナスの金額・null や空の通貨は IllegalArgumentException")
    void invalidConstructorArgs() {
        assertThrows(IllegalArgumentException.class, () -> new Money(-1, "JPY"),
                "金額がマイナスなら IllegalArgumentException を投げてください");
        assertThrows(IllegalArgumentException.class, () -> new Money(100, null),
                "通貨が null なら IllegalArgumentException を投げてください");
        assertThrows(IllegalArgumentException.class, () -> new Money(100, ""),
                "通貨が空文字なら IllegalArgumentException を投げてください");
    }

    @Test
    @DisplayName("plus は新しいインスタンスを返し、元の Money は変わらない")
    void plusReturnsNewInstance() {
        Money a = new Money(1000, "JPY");
        Money b = new Money(250, "JPY");
        Money sum = a.plus(b);
        assertEquals(1250, sum.getAmount(), "1000 + 250 = 1250 のはずです");
        assertEquals("JPY", sum.getCurrency(), "合計の通貨は元と同じ JPY のはずです");
        assertNotSame(a, sum, "plus は自分を返さず、new Money(...) で新しいインスタンスを返してください");
        assertEquals(1000, a.getAmount(), "plus を呼んでも元の Money の金額は変わってはいけません");
        assertEquals(250, b.getAmount(), "plus の引数の Money も変わってはいけません");
    }

    @Test
    @DisplayName("通貨が違う・null を plus すると IllegalArgumentException")
    void plusInvalid() {
        Money yen = new Money(1000, "JPY");
        assertThrows(IllegalArgumentException.class, () -> yen.plus(new Money(10, "USD")),
                "JPY と USD は足せないので IllegalArgumentException を投げてください");
        assertThrows(IllegalArgumentException.class, () -> yen.plus(null),
                "null を足そうとしたら IllegalArgumentException を投げてください(NullPointerException ではなく)");
        Money yen2 = yen.plus(new Money(1, new String("JPY")));
        assertEquals(1001, yen2.getAmount(), "通貨コードは equals で比べてください(== だと同じ JPY でも別物扱いになります)");
    }

    @Test
    @DisplayName("times は n 倍した新しい Money を返す。0倍は0、マイナスは例外")
    void times() {
        Money price = new Money(1200, "JPY");
        assertEquals("3600 JPY", price.times(3).toString(), "1200 の 3 倍は 3600 JPY のはずです");
        assertEquals(1200, price.getAmount(), "times を呼んでも元の Money は変わってはいけません");
        assertEquals(0, price.times(0).getAmount(), "0 倍は金額 0 のはずです");
        assertThrows(IllegalArgumentException.class, () -> price.times(-1),
                "マイナス倍なら IllegalArgumentException を投げてください");
    }
}
