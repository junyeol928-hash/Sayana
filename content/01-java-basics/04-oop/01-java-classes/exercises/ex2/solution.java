public class BankAccount {
    String owner;
    int balance;

    BankAccount(String owner, int balance) {
        this.owner = owner;
        this.balance = balance;
    }

    BankAccount(String owner) {
        this(owner, 0);
    }

    boolean deposit(int amount) {
        if (amount <= 0) {
            return false;
        }
        balance += amount;
        return true;
    }

    boolean withdraw(int amount) {
        if (amount <= 0 || amount > balance) {
            return false;
        }
        balance -= amount;
        return true;
    }

    boolean transferTo(BankAccount to, int amount) {
        if (to == null) {
            return false;
        }
        if (!withdraw(amount)) {
            return false;
        }
        to.deposit(amount);
        return true;
    }

    public static void main(String[] args) {
        BankAccount haru = new BankAccount("ハル", 10000);
        BankAccount moka = new BankAccount("モカ");
        System.out.println(haru.transferTo(moka, 3000)); // true
        System.out.println(haru.balance + " / " + moka.balance); // 7000 / 3000
        System.out.println(haru.withdraw(8000)); // false
    }
}
