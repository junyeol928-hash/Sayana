public class SafeAccount {
    private int balance;

    SafeAccount(int initial) {
        if (initial < 0) {
            throw new IllegalArgumentException(
                    "初期残高は0以上: " + initial);
        }
        this.balance = initial;
    }

    int getBalance() {
        return balance;
    }

    void deposit(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "入金額は1以上: " + amount);
        }
        balance += amount;
    }

    boolean withdraw(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "出金額は1以上: " + amount);
        }
        if (amount > balance) {
            return false;
        }
        balance -= amount;
        return true;
    }

    public static void main(String[] args) {
        SafeAccount acc = new SafeAccount(1000);
        System.out.println(acc.withdraw(1500)); // false
        System.out.println(acc.withdraw(1000)); // true
        System.out.println(acc.getBalance());   // 0
    }
}
