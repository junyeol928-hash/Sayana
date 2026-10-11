public class Employee {
    private final String name;

    Employee(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }

    /** 月給(円)。子クラスでオーバーライドする */
    int monthlyPay() {
        return 0;
    }

    String payslip() {
        return name + ": " + monthlyPay() + "円";
    }

    public static void main(String[] args) {
        System.out.println(new FullTimeEmployee("田中", 300000, 10).payslip()); // 田中: 325000円
        System.out.println(new PartTimeEmployee("高橋", 1000, 170).payslip()); // 高橋: 172500円
        System.out.println(new Manager("佐藤", 400000).payslip());            // 佐藤: 450000円
    }
}

class FullTimeEmployee extends Employee {
    static final int OVERTIME_RATE = 2500;
    private final int baseSalary;
    private final int overtimeHours;

    FullTimeEmployee(String name, int baseSalary, int overtimeHours) {
        super(name);
        this.baseSalary = baseSalary;
        this.overtimeHours = overtimeHours;
    }

    // TODO: monthlyPay() をオーバーライドし、基本給 + 残業時間 × OVERTIME_RATE を返す
}

class PartTimeEmployee extends Employee {
    static final int REGULAR_LIMIT = 160;
    private final int hourlyWage;
    private final int hours;

    PartTimeEmployee(String name, int hourlyWage, int hours) {
        super(name);
        this.hourlyWage = hourlyWage;
        this.hours = hours;
    }

    // TODO: monthlyPay() をオーバーライドする
    //       160 時間までは 時給 × 時間、超えた分は 時給 × 125 / 100 × 超過時間
}

class Manager extends FullTimeEmployee {
    static final int ALLOWANCE = 50000;

    Manager(String name, int baseSalary) {
        // TODO: 残業時間 0 として親(FullTimeEmployee)のコンストラクタに渡す
        super(name, 0, 0);
    }

    // TODO: monthlyPay() をオーバーライドし、super.monthlyPay() + ALLOWANCE を返す
}
