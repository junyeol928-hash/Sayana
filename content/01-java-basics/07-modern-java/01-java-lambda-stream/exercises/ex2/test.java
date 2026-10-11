import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeptReportTest {

    private static List<Employee> sample() {
        return List.of(
                new Employee("E01", "佐藤", "開発", 42),
                new Employee("E02", "鈴木", "営業", 38),
                new Employee("E03", "田中", "開発", 51),
                new Employee("E04", "伊藤", "総務", 35),
                new Employee("E05", "渡辺", "営業", 40));
    }

    @Test
    @DisplayName("部署ごとの人数(開発2・営業2・総務1)")
    void headcount() {
        Map<String, Long> m = DeptReport.headcount(sample());
        assertEquals(3, m.size(), "部署は 開発・営業・総務 の3つです。Map のキーが部署名になっているか確認してください");
        assertEquals(2L, m.get("開発"), "開発は佐藤・田中の2人です");
        assertEquals(2L, m.get("営業"), "営業は鈴木・渡辺の2人です");
        assertEquals(1L, m.get("総務"), "総務は伊藤の1人です");
    }

    @Test
    @DisplayName("部署ごとの月給の合計(開発 42 + 51 = 93)")
    void salaryTotal() {
        Map<String, Integer> m = DeptReport.salaryTotal(sample());
        assertEquals(93, m.get("開発"), "開発の月給合計は 42 + 51 = 93 です。summingInt(Employee::getSalary) を使っていますか?");
        assertEquals(78, m.get("営業"), "営業の月給合計は 38 + 40 = 78 です");
        assertEquals(35, m.get("総務"), "総務は1人なので 35 です");
    }

    @Test
    @DisplayName("ID から氏名を引ける Map")
    void nameById() {
        Map<String, String> m = DeptReport.nameById(sample());
        assertEquals(5, m.size(), "5人分のエントリが必要です");
        assertEquals("佐藤", m.get("E01"), "E01 は佐藤さんです。キーが ID、値が氏名になっているか確認してください");
        assertEquals("渡辺", m.get("E05"), "E05 は渡辺さんです");
    }

    @Test
    @DisplayName("ID が重複したら後の社員の氏名を採用する(例外で落ちない)")
    void nameByIdDuplicate() {
        List<Employee> dup = List.of(
                new Employee("E01", "佐藤", "開発", 42),
                new Employee("E01", "佐藤(旧姓 高橋)", "開発", 42));
        Map<String, String> m = DeptReport.nameById(dup);
        assertEquals("佐藤(旧姓 高橋)", m.get("E01"),
                "ID が重複したら後に出てきた方を採用してください。toMap の第3引数で (oldName, newName) -> newName を渡します");
    }

    @Test
    @DisplayName("社員がいなければ、どれも空の Map(null ではない)")
    void empty() {
        Map<String, Long> h = DeptReport.headcount(List.of());
        Map<String, Integer> s = DeptReport.salaryTotal(List.of());
        Map<String, String> n = DeptReport.nameById(List.of());
        assertNotNull(h, "null ではなく空の Map を返してください");
        assertNotNull(s, "null ではなく空の Map を返してください");
        assertNotNull(n, "null ではなく空の Map を返してください");
        assertTrue(h.isEmpty() && s.isEmpty() && n.isEmpty(), "社員が0人なら、どの Map も空になるはずです");
    }
}
