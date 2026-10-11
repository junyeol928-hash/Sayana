import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeFinderTest {

    private static List<Employee> sample() {
        return List.of(
                new Employee("E01", "佐藤"),
                new Employee("E02", "鈴木"),
                new Employee("E03", "田中"));
    }

    @Test
    @DisplayName("ID が見つかれば、その社員が入った Optional を返す")
    void found() {
        Optional<Employee> r = EmployeeFinder.findById(sample(), "E02");
        assertNotNull(r, "Optional を返すメソッドは null を返してはいけません");
        assertTrue(r.isPresent(), "E02 の社員(鈴木)が見つかるはずです。filter の条件を確認してください");
        assertEquals("鈴木", r.orElseThrow().getName(), "E02 は鈴木さんです");
    }

    @Test
    @DisplayName("ID が見つからなければ空の Optional(null ではない)")
    void notFound() {
        Optional<Employee> r = EmployeeFinder.findById(sample(), "E99");
        assertNotNull(r, "見つからないときも null ではなく Optional.empty() を返してください");
        assertTrue(r.isEmpty(), "E99 はいないので空の Optional になるはずです");
    }

    @Test
    @DisplayName("実行時に組み立てた ID でも見つかる(equals で比べる)")
    void builtId() {
        String id = new StringBuilder("E0").append(1).toString();
        assertEquals("佐藤", EmployeeFinder.nameOf(sample(), id),
                "ID は == ではなく equals で比べてください");
    }

    @Test
    @DisplayName("nameOf:見つかれば氏名、なければ \"(不明)\"")
    void nameOf() {
        assertEquals("田中", EmployeeFinder.nameOf(sample(), "E03"), "E03 は田中さんです");
        assertEquals("(不明)", EmployeeFinder.nameOf(sample(), "E99"),
                "見つからないときは orElse で \"(不明)\" を返してください");
    }

    @Test
    @DisplayName("ID が重複していたら先頭に近い社員を返す")
    void duplicate() {
        List<Employee> dup = List.of(
                new Employee("E01", "佐藤"),
                new Employee("E01", "高橋"));
        assertEquals("佐藤", EmployeeFinder.nameOf(dup, "E01"),
                "同じ ID が複数あるときは先頭に近い方(findFirst)を返してください");
    }

    @Test
    @DisplayName("名簿が空なら空の Optional と \"(不明)\"")
    void empty() {
        assertTrue(EmployeeFinder.findById(List.of(), "E01").isEmpty(), "空の名簿からは誰も見つかりません");
        assertEquals("(不明)", EmployeeFinder.nameOf(List.of(), "E01"), "空の名簿なら \"(不明)\" です");
    }
}
