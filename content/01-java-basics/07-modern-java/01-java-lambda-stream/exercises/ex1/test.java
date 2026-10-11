import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderTotalTest {

    private static List<Order> sample() {
        return List.of(
                new Order("佐藤", "PAID", 3000),
                new Order("鈴木", "CANCELLED", 5000),
                new Order("田中", "PAID", 4500),
                new Order("伊藤", "PENDING", 1200));
    }

    @Test
    @DisplayName("支払済み(PAID)の合計は 3000 + 4500 = 7500 円")
    void totalPaid() {
        assertEquals(7500, OrderTotal.totalByStatus(sample(), "PAID"),
                "PAID の注文(佐藤 3000、田中 4500)だけを合計してください。キャンセル分まで足していませんか?");
    }

    @Test
    @DisplayName("状態を変えると対象も変わる(CANCELLED は 5000 円)")
    void totalCancelled() {
        assertEquals(5000, OrderTotal.totalByStatus(sample(), "CANCELLED"),
                "引数 status で絞り込んでください。\"PAID\" を決め打ちしていませんか?");
    }

    @Test
    @DisplayName("一致する注文がない・リストが空なら合計は 0")
    void totalNone() {
        assertEquals(0, OrderTotal.totalByStatus(sample(), "REFUNDED"),
                "一致する注文がないときは 0 を返してください");
        assertEquals(0, OrderTotal.totalByStatus(List.of(), "PAID"),
                "空のリストなら合計は 0 です");
    }

    @Test
    @DisplayName("実行時に組み立てた文字列でも一致する(equals で比べる)")
    void totalWithBuiltString() {
        String status = new StringBuilder("PA").append("ID").toString();
        assertEquals(7500, OrderTotal.totalByStatus(sample(), status),
                "文字列は == ではなく equals で比べてください。== だと中身が同じでも別オブジェクトなら false になります");
    }

    @Test
    @DisplayName("min ちょうどの金額も件数に含める(境界値)")
    void countBoundary() {
        assertEquals(2, OrderTotal.countAtLeast(sample(), 4500),
                "4500 以上は 5000 と 4500 の2件です。> ではなく >= で比べていますか?");
        assertEquals(1, OrderTotal.countAtLeast(sample(), 4501),
                "4501 以上は 5000 の1件だけです");
    }

    @Test
    @DisplayName("全件・0件・空リストの件数")
    void countAllOrNone() {
        assertEquals(4, OrderTotal.countAtLeast(sample(), 0),
                "min が 0 なら、状態に関係なく全4件を数えてください");
        assertEquals(0, OrderTotal.countAtLeast(sample(), 10000),
                "10000 円以上の注文はないので 0 件です");
        assertEquals(0, OrderTotal.countAtLeast(List.of(), 0),
                "空のリストなら 0 件です");
    }
}
