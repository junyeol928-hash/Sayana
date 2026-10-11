import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class Employee {
    private final String id;
    private final String name;
    private final String dept;
    private final int salary;

    Employee(String id, String name, String dept, int salary) {
        this.id = id;
        this.name = name;
        this.dept = dept;
        this.salary = salary;
    }

    String getId() { return id; }
    String getName() { return name; }
    String getDept() { return dept; }
    int getSalary() { return salary; }
}

public class DeptReport {

    public static Map<String, Long> headcount(List<Employee> emps) {
        return emps.stream()
                .collect(Collectors.groupingBy(
                        Employee::getDept, Collectors.counting()));
    }

    public static Map<String, Integer> salaryTotal(List<Employee> emps) {
        return emps.stream()
                .collect(Collectors.groupingBy(
                        Employee::getDept,
                        Collectors.summingInt(Employee::getSalary)));
    }

    public static Map<String, String> nameById(List<Employee> emps) {
        return emps.stream()
                .collect(Collectors.toMap(
                        Employee::getId,
                        Employee::getName,
                        (oldName, newName) -> newName));
    }

    public static void main(String[] args) {
        List<Employee> emps = List.of(
                new Employee("E01", "佐藤", "開発", 42),
                new Employee("E02", "鈴木", "営業", 38),
                new Employee("E03", "田中", "開発", 51));
        System.out.println(headcount(emps));   // {開発=2, 営業=1}(順不同)
        System.out.println(salaryTotal(emps)); // {開発=93, 営業=38}(順不同)
        System.out.println(nameById(emps));    // {E01=佐藤, E02=鈴木, E03=田中}(順不同)
    }
}
