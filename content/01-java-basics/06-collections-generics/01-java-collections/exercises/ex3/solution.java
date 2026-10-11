import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DeptRoster {

    public static Map<String, List<String>> groupByDept(List<Employee> employees) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        Set<Employee> seen = new HashSet<>();
        for (Employee e : employees) {
            if (!seen.add(e)) {
                continue; // すでに処理した社員(重複データ)
            }
            List<String> names = result.get(e.getDept());
            if (names == null) {
                names = new ArrayList<>();
                result.put(e.getDept(), names);
            }
            names.add(e.getName());
        }
        return result;
    }

    public static void main(String[] args) {
        List<Employee> list = List.of(
                new Employee("E01", "佐藤", "営業"),
                new Employee("E02", "鈴木", "開発"),
                new Employee("E03", "高橋", "営業"),
                new Employee("E01", "佐藤", "営業"));
        System.out.println(groupByDept(list)); // {営業=[佐藤, 高橋], 開発=[鈴木]}
    }
}

class Employee {
    private final String id;
    private final String name;
    private final String dept;

    Employee(String id, String name, String dept) {
        this.id = id;
        this.name = name;
        this.dept = dept;
    }

    String getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getDept() {
        return dept;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Employee e && Objects.equals(id, e.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id + ":" + name + "(" + dept + ")";
    }
}
