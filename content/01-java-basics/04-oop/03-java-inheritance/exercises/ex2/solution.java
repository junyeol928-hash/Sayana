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

    @Override
    int monthlyPay() {
        return baseSalary + overtimeHours * OVERTIME_RATE;
    }
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

    @Override
    int monthlyPay() {
        if (hours <= REGULAR_LIMIT) {
            return hourlyWage * hours;
        }
        int overtime = hours - REGULAR_LIMIT;
        return hourlyWage * REGULAR_LIMIT + hourlyWage * 125 / 100 * overtime;
    }
}

class Manager extends FullTimeEmployee {
    static final int ALLOWANCE = 50000;

    Manager(String name, int baseSalary) {
        super(name, baseSalary, 0);
    }

    @Override
    int monthlyPay() {
        return super.monthlyPay() + ALLOWANCE;
    }
}
