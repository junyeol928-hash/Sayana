import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExpenseReportTest {

    @Test
    @DisplayName("total:Integer / Double / Long のどのリストでも合計できる")
    void totalVariousTypes() {
        assertEquals(5000.0, ExpenseReport.total(List.of(1200, 800, 3000)), 1e-9,
                "Integer のリスト 1200 + 800 + 3000 の合計は 5000.0 です");
        assertEquals(30.75, ExpenseReport.total(List.of(10.5, 20.25)), 1e-9,
                "Double のリストは小数も含めて合計してください。intValue() ではなく doubleValue() を使っていますか?");
        assertEquals(3_000_000_000.0, ExpenseReport.total(List.of(1_500_000_000L, 1_500_000_000L)), 1e-3,
                "Long のリストも合計できるはずです。int で足すと桁あふれします。double で合計してください");
    }

    @Test
    @DisplayName("total:null の要素は読み飛ばし、null や空のリストなら 0.0")
    void totalNullAndEmpty() {
        List<Integer> withNull = new ArrayList<>(Arrays.asList(1000, null, 500));
        assertEquals(1500.0, assertDoesNotThrow(() -> ExpenseReport.total(withNull),
                "null の要素で例外が出ています。n != null のときだけ足してください"), 1e-9,
                "null の要素を除いた合計は 1500.0 です");
        assertEquals(0.0, ExpenseReport.total(List.of()), 1e-9, "空のリストなら 0.0 を返してください");
        assertEquals(0.0, ExpenseReport.total(null), 1e-9, "amounts が null なら 0.0 を返してください");
    }

    @Test
    @DisplayName("collectLarge:しきい値以上だけを double で追加し、件数を返す(境界値を含む)")
    void collectBoundary() {
        List<Double> dest = new ArrayList<>();
        int n = ExpenseReport.collectLarge(List.of(3000, 52000, 50000, 49999, 800), 50000, dest);
        assertEquals(2, n, "50000 以上は 52000 と 50000 の2件です。> ではなく >= で比べていますか?");
        assertEquals(List.of(52000.0, 50000.0), dest,
                "dest には条件を満たす金額を、元の順番で double にして追加してください。実際の結果: " + dest);
    }

    @Test
    @DisplayName("collectLarge:dest が List<Number> や List<Object> でも使え、既存の要素の後ろに追加する")
    void collectIntoSuperTypes() {
        List<Number> numbers = new ArrayList<>(List.of(1));
        assertEquals(1, ExpenseReport.collectLarge(List.of(10.5, 99.9), 50, numbers), "50 以上は 99.9 の1件です");
        assertEquals(List.of(1, 99.9), numbers,
                "dest の既存の要素は残したまま、末尾に追加してください。実際の結果: " + numbers);

        List<Object> objects = new ArrayList<>();
        assertEquals(2, ExpenseReport.collectLarge(List.of(100L, 200L), 100, objects), "100 以上は2件です");
        assertEquals(List.of(100.0, 200.0), objects,
                "Long の値も double(Double)に変換して追加してください。実際の結果: " + objects);
    }

    @Test
    @DisplayName("collectLarge:src の null 要素は読み飛ばし、src が null なら 0 件")
    void collectNull() {
        List<Double> dest = new ArrayList<>();
        List<Integer> src = new ArrayList<>(Arrays.asList(null, 70000, null));
        assertEquals(1, assertDoesNotThrow(() -> ExpenseReport.collectLarge(src, 50000, dest),
                "src の null 要素で例外が出ています。null は読み飛ばしてください"), "70000 の1件だけが対象です");
        assertEquals(List.of(70000.0), dest, "null を除いた 70000.0 だけが追加されるはずです");
        assertEquals(3, src.size(), "src のリストは変更しないでください");

        List<Double> empty = new ArrayList<>();
        assertEquals(0, ExpenseReport.collectLarge(null, 0, empty), "src が null なら 0 を返してください");
        assertTrue(empty.isEmpty(), "src が null なら dest には何も追加しないでください");
    }
}
