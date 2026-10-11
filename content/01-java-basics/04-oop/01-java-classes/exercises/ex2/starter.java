public class BankAccount {
    String owner;
    int balance;

    BankAccount(String owner, int balance) {
        // TODO: 名義と初期残高をフィールドに入れる
    }

    BankAccount(String owner) {
        // TODO: this(...) で上のコンストラクタを呼び、初期残高 0 で作る
    }

    boolean deposit(int amount) {
        // TODO: amount が 0 以下なら false。そうでなければ残高を増やして true
        return false; // ← ダミー。書き換えてください
    }

    boolean withdraw(int amount) {
        // TODO: amount が 0 以下、または残高より多いなら false
        // TODO: そうでなければ残高を減らして true
        return false; // ← ダミー。書き換えてください
    }

    boolean transferTo(BankAccount to, int amount) {
        // TODO: to が null なら false
        // TODO: 自分から出金できたら、to に入金して true
        return false; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        BankAccount haru = new BankAccount("ハル", 10000);
        BankAccount moka = new BankAccount("モカ");
        System.out.println(haru.transferTo(moka, 3000)); // true
        System.out.println(haru.balance + " / " + moka.balance); // 7000 / 3000
        System.out.println(haru.withdraw(8000)); // false
    }
}
