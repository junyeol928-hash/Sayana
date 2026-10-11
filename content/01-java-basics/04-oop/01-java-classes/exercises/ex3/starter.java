public class Payroll {

    static int total(Employee[] staff) {
        // TODO: null の要素を飛ばしながら、全員の salary を合計して返す
        return 0; // ← ダミー。書き換えてください
    }

    static int totalByDept(Employee[] staff, String dept) {
        // TODO: null を飛ばし、部署が dept と等しい(equals)社員の salary を合計して返す
        return 0; // ← ダミー。書き換えてください
    }

    static Employee topEarner(Employee[] staff) {
        // TODO: 月給が一番高い社員を返す(同額なら先の社員。1人もいなければ null)
        return null; // ← ダミー。書き換えてください
    }

    static void raiseAll(Employee[] staff, int percent) {
        // TODO: null を飛ばし、各社員の salary に salary * percent / 100 を足す
    }

    public static void main(String[] args) {
        Employee[] staff = {
            new Employee("佐藤", "営業", 300000),
            null,
            new Employee("鈴木", "開発", 350000),
            new Employee("高橋", "営業", 280000),
        };
        System.out.println(total(staff));              // 930000
        System.out.println(totalByDept(staff, "営業")); // 580000
        System.out.println(topEarner(staff));          // 鈴木(開発 350000円)
        raiseAll(staff, 10);
        System.out.println(staff[0]);                  // 佐藤(営業 330000円)
    }
}

// ↓ 社員クラス(完成済み。変更しなくてOK)
class Employee {
    String name;
    String dept;
    int salary;

    Employee(String name, String dept, int salary) {
        this.name = name;
        this.dept = dept;
        this.salary = salary;
    }

    @Override
    public String toString() {
        return name + "(" + dept + " " + salary + "円)";
    }
}
