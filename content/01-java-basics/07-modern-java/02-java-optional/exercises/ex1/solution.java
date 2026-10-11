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
        return emps.stream()
                .filter(e -> e.getId().equals(id))
                .findFirst();
    }

    public static String nameOf(List<Employee> emps, String id) {
        return findById(emps, id)
                .map(Employee::getName)
                .orElse("(不明)");
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
