import java.util.List;
import java.util.Optional;

class Employee {
    private final String id;
    private final String name;

    Employee(String id, String name) {
        this.id = id;
        this.name = name;
    }

    String getId() { return id; }
    String getName() { return name; }
}

public class EmployeeFinder {

    public static Optional<Employee> findById(List<Employee> emps, String id) {
        // TODO: ID が一致する最初の社員を返す(filter → findFirst)
        return Optional.empty();
    }

    public static String nameOf(List<Employee> emps, String id) {
        // TODO: findById の結果を map で氏名に変換し、なければ "(不明)" を返す
        return "";
    }

    public static void main(String[] args) {
        List<Employee> emps = List.of(
                new Employee("E01", "佐藤"),
                new Employee("E02", "鈴木"),
                new Employee("E03", "田中"));
        System.out.println(findById(emps, "E02").isPresent()); // true
        System.out.println(nameOf(emps, "E03"));               // 田中
        System.out.println(nameOf(emps, "E99"));               // (不明)
    }
}
