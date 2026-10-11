import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeptRosterTest {

    @Test
    @DisplayName("equals:社員番号が同じなら同じ社員(氏名・部署が違っても)")
    void equalsById() {
        Employee a = new Employee("E01", "佐藤", "営業");
        Employee b = new Employee(new String("E01"), "佐藤(旧姓 田中)", "開発");
        Employee c = new Employee("E02", "佐藤", "営業");
        assertTrue(a.equals(b), "社員番号が同じ E01 同士は equals が true になるはずです。id を Objects.equals で比べていますか?");
        assertFalse(a.equals(c), "社員番号が違う E01 と E02 は equals が false になるはずです");
        assertFalse(a.equals(null), "null と比べたら false を返してください(例外にしない)");
        assertFalse(a.equals("E01"), "Employee 以外のオブジェクトと比べたら false を返してください");
    }

    @Test
    @DisplayName("hashCode:equals が true なら hashCode も同じ")
    void hashCodeConsistent() {
        Employee a = new Employee("E01", "佐藤", "営業");
        Employee b = new Employee(new String("E01"), "佐藤", "開発");
        assertEquals(a.hashCode(), b.hashCode(),
                "社員番号が同じなら hashCode も同じ値にしてください。hashCode をオーバーライドしていますか?(Objects.hash(id))");
    }

    @Test
    @DisplayName("HashSet に同じ社員を2回入れても1件になる")
    void worksInHashSet() {
        Set<Employee> set = new HashSet<>();
        set.add(new Employee("E01", "佐藤", "営業"));
        set.add(new Employee("E01", "佐藤", "営業"));
        assertEquals(1, set.size(),
                "同じ社員番号なのに HashSet に2件入っています。equals と hashCode を両方オーバーライドしてください");
        assertTrue(set.contains(new Employee("E01", "佐藤", "営業")),
                "contains で見つかりません。equals だけでなく hashCode もオーバーライドしていますか?");
    }

    @Test
    @DisplayName("groupByDept:部署ごとに氏名をまとめ、部署も氏名も出てきた順に並べる")
    void groupInOrder() {
        Map<String, List<String>> r = DeptRoster.groupByDept(List.of(
                new Employee("E01", "佐藤", "営業"),
                new Employee("E02", "鈴木", "開発"),
                new Employee("E03", "高橋", "営業"),
                new Employee("E04", "伊藤", "総務"),
                new Employee("E05", "渡辺", "開発")));
        assertEquals(Map.of("営業", List.of("佐藤", "高橋"), "開発", List.of("鈴木", "渡辺"), "総務", List.of("伊藤")), r,
                "部署ごとの名簿が正しくありません。実際の結果: " + r);
        assertEquals(List.of("営業", "開発", "総務"), List.copyOf(r.keySet()),
                "部署が最初に出てきた順になっていません。HashMap ではなく LinkedHashMap を使ってください。実際の順: " + r.keySet());
    }

    @Test
    @DisplayName("groupByDept:二重に取り込まれた社員は最初の1件だけ使う")
    void skipDuplicates() {
        Map<String, List<String>> r = DeptRoster.groupByDept(List.of(
                new Employee("E01", "佐藤", "営業"),
                new Employee("E02", "鈴木", "開発"),
                new Employee("E03", "高橋", "営業"),
                new Employee("E01", "佐藤", "営業"),
                new Employee("E02", "鈴木", "総務")));
        assertEquals(List.of("佐藤", "高橋"), r.get("営業"),
                "営業部に佐藤さんが重複しています。処理済みの社員を Set に覚えて、add が false なら読み飛ばしてください。実際の結果: " + r);
        assertEquals(List.of("鈴木"), r.get("開発"), "開発部は鈴木さん1人です。実際の結果: " + r);
        assertFalse(r.containsKey("総務"),
                "E02 は2件目(総務)が重複なので使いません。総務部は作られないはずです。実際の結果: " + r);
    }

    @Test
    @DisplayName("groupByDept:空のリストなら空の Map")
    void empty() {
        Map<String, List<String>> r = DeptRoster.groupByDept(List.of());
        assertNotNull(r, "null ではなく空の Map を返してください");
        assertTrue(r.isEmpty(), "社員が0人なら空の Map を返してください");
    }
}
