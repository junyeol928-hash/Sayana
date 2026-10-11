public class SafeAccount {
    int balance; // TODO: 外から書き換えられないように private にする

    SafeAccount(int initial) {
        // TODO: initial が 0 未満なら IllegalArgumentException を投げる
        // TODO: 残高を設定する
    }

    int getBalance() {
        // TODO: 残高を返す
        return -1; // ← ダミー。書き換えてください
    }

    void deposit(int amount) {
        // TODO: amount が 0 以下なら IllegalArgumentException を投げる
        // TODO: 残高を増やす
    }

    boolean withdraw(int amount) {
        // TODO: amount が 0 以下なら IllegalArgumentException を投げる
        // TODO: 残高不足なら false。そうでなければ残高を減らして true
        return false; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        SafeAccount acc = new SafeAccount(1000);
        System.out.println(acc.withdraw(1500)); // false
        System.out.println(acc.withdraw(1000)); // true
        System.out.println(acc.getBalance());   // 0
    }
}
