public class Payroll {

    static int total(Employee[] staff) {
        int sum = 0;
        for (Employee e : staff) {
            if (e == null) {
                continue;
            }
            sum += e.salary;
        }
        return sum;
    }

    static int totalByDept(Employee[] staff, String dept) {
        int sum = 0;
        for (Employee e : staff) {
            if (e != null && e.dept.equals(dept)) {
                sum += e.salary;
            }
        }
        return sum;
    }

    static Employee topEarner(Employee[] staff) {
        Employee top = null;
        for (Employee e : staff) {
            if (e == null) {
                continue;
            }
            if (top == null || e.salary > top.salary) {
                top = e;
            }
        }
        return top;
    }

    static void raiseAll(Employee[] staff, int percent) {
        for (Employee e : staff) {
            if (e != null) {
                e.salary += e.salary * percent / 100;
            }
        }
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
