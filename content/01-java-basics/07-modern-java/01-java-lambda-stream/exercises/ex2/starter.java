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
        // TODO: groupingBy で部署ごとに分けて、counting() で人数を数える
        return Map.of();
    }

    public static Map<String, Integer> salaryTotal(List<Employee> emps) {
        // TODO: groupingBy の第2引数に summingInt を渡して、部署ごとの月給の合計を出す
        return Map.of();
    }

    public static Map<String, String> nameById(List<Employee> emps) {
        // TODO: toMap で ID → 氏名 の Map を作る
        //       ID が重複したら後の社員の氏名を採用する(第3引数)
        return Map.of();
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
