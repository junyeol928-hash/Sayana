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
        // TODO: 結果用の LinkedHashMap と、処理済みの社員を覚える HashSet を用意する

        // TODO: 1人ずつ処理する
        //   - Set への add が false なら(すでに処理済みの社員なら)読み飛ばす
        //   - 部署のリストがなければ新しい ArrayList を作って put する
        //   - そのリストに氏名を add する

        return new HashMap<>(); // ← ダミー。書き換えてください
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

    // TODO: id が同じなら true を返す equals(Object o) をオーバーライドする

    // TODO: id から計算する hashCode() をオーバーライドする

    @Override
    public String toString() {
        return id + ":" + name + "(" + dept + ")";
    }
}
