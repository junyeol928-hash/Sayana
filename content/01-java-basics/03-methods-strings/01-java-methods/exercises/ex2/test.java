import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockUpdaterTest {

    @Test
    @DisplayName("出荷すると呼び出し元の配列の在庫が減る")
    void shipUpdatesCallersArray() {
        int[] stock = {10, 0, 5};
        assertTrue(StockUpdater.ship(stock, 0, 3), "在庫 10 から 3 個は出荷できるので true を返してください");
        assertArrayEquals(new int[]{7, 0, 5}, stock,
                "出荷後、呼び出し元の配列が {7, 0, 5} になっていません。stock[index] から qty を引いていますか?");
    }

    @Test
    @DisplayName("在庫ちょうどの出荷はOKで在庫 0、1個でも足りなければ false で配列は変えない(境界値)")
    void shipBoundary() {
        int[] stock = {5, 2};
        assertTrue(StockUpdater.ship(stock, 0, 5), "在庫 5 から 5 個はちょうど出荷できます。条件が < になっているか確認してください");
        assertArrayEquals(new int[]{0, 2}, stock, "在庫ちょうど出荷したら在庫は 0 です");
        assertFalse(StockUpdater.ship(stock, 1, 3), "在庫 2 から 3 個は出荷できないので false です");
        assertArrayEquals(new int[]{0, 2}, stock, "出荷できなかったときは配列を一切変えないでください(マイナス在庫になっていませんか?)");
    }

    @Test
    @DisplayName("範囲外の index・0 以下の数量・null は false(例外にしない)")
    void shipInvalidInput() {
        int[] stock = {10, 10};
        assertFalse(StockUpdater.ship(stock, -1, 1), "index が -1 なら false を返してください");
        assertFalse(StockUpdater.ship(stock, 2, 1), "長さ 2 の配列で index 2 は範囲外です。index >= stock.length のチェックを入れましょう");
        assertFalse(StockUpdater.ship(stock, 0, 0), "数量 0 の出荷は false です");
        assertFalse(StockUpdater.ship(stock, 0, -3), "数量がマイナスなら false です(在庫が増えてしまいます)");
        assertArrayEquals(new int[]{10, 10}, stock, "false を返すときは配列を変えないでください");
        assertDoesNotThrow(() -> StockUpdater.ship(null, 0, 1), "stock が null のとき例外が出ています。最初に null チェックをしてください");
        assertFalse(StockUpdater.ship(null, 0, 1), "stock が null なら false を返してください");
    }

    @Test
    @DisplayName("restocked は全商品に qty を足した新しい配列を返し、元の配列は変えない")
    void restockedReturnsNewArray() {
        int[] stock = {7, 0, 5};
        int[] next = StockUpdater.restocked(stock, 2);
        assertArrayEquals(new int[]{9, 2, 7}, next, "restocked(stock, 2) は {9, 2, 7} を返すはずです");
        assertArrayEquals(new int[]{7, 0, 5}, stock,
                "元の配列が書き換わっています。stock[i] += qty ではなく、新しい配列に stock[i] + qty を入れてください");
        assertNotSame(stock, next, "引数の配列をそのまま返しています。new int[stock.length] で新しい配列を作ってください");
    }

    @Test
    @DisplayName("restocked に null や空配列を渡すと長さ 0 の配列が返る")
    void restockedNullOrEmpty() {
        assertArrayEquals(new int[0], StockUpdater.restocked(null, 5), "stock が null なら new int[0] を返してください");
        assertArrayEquals(new int[0], StockUpdater.restocked(new int[0], 5), "空配列なら結果も空配列です");
    }
}
