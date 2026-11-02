import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTest {

    @Test
    @DisplayName("Builder は static ネストクラスになっている")
    void builderIsStatic() {
        assertTrue(Modifier.isStatic(Order.Builder.class.getModifiers()),
                "Builder に static を付けてください。static がないと new Order.Builder() で作れません");
    }

    @Test
    @DisplayName("build() で Order が作られる")
    void buildReturnsOrder() {
        assertNotNull(new Order.Builder().build(),
                "build() が null を返しています。new Order(this) で Order を作って返してください");
    }

    @Test
    @DisplayName("customer と item は Builder 自身を返す(メソッドチェーンできる)")
    void chainReturnsSameBuilder() {
        Order.Builder b = new Order.Builder();
        assertSame(b, b.customer("佐藤"), "customer(...) は this を返してください");
        assertSame(b, b.item(100), "item(...) は this を返してください");
    }

    @Test
    @DisplayName("顧客名と商品2点で注文を組み立てる")
    void typicalOrder() {
        Order o = new Order.Builder()
                .customer("佐藤")
                .item(1200)
                .item(800)
                .build();
        assertNotNull(o, "build() が null を返しています");
        assertEquals("佐藤", o.customer(), "customer(\"佐藤\") で渡した名前が Order に入っていません");
        assertEquals(2, o.itemCount(), "item(...) を2回呼んだら点数は 2 です。item の中で点数を1増やしてください");
        assertEquals(2000, o.total(), "合計は 1200 + 800 = 2000 円です。item の中で total に price を足してください");
        assertEquals("佐藤様: 2点 2000円", o.summary(), "summary() の結果が違います");
    }

    @Test
    @DisplayName("customer を呼ばなければ「ゲスト」、item を呼ばなければ 0点 0円")
    void defaults() {
        Order o = new Order.Builder().build();
        assertNotNull(o, "build() が null を返しています");
        assertEquals("ゲスト", o.customer(),
                "customer(...) を呼ばなかったときは \"ゲスト\" になるよう、Builder のフィールドに初期値を付けてください");
        assertEquals("ゲスト様: 0点 0円", o.summary(), "何も指定しない注文は \"ゲスト様: 0点 0円\" です");
    }

    @Test
    @DisplayName("Builder ごとに別々の注文になる")
    void buildersAreIndependent() {
        Order a = new Order.Builder().customer("鈴木").item(500).build();
        Order b = new Order.Builder().customer("高橋").item(300).item(300).item(300).build();
        assertNotNull(a, "build() が null を返しています");
        assertNotNull(b, "build() が null を返しています");
        assertEquals("鈴木様: 1点 500円", a.summary(), "1つ目の注文の内容が違います");
        assertEquals("高橋様: 3点 900円", b.summary(),
                "2つ目の注文の内容が違います。点数や合計を static フィールドにしていませんか?");
    }
}
