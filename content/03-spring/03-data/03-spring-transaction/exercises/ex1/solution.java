import java.util.HashMap;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

public class TransferService {

    private final AccountStore store;

    public TransferService(AccountStore store) {
        this.store = store;
    }

    @Transactional
    public void transfer(String from, String to, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("振込金額は1円以上にしてください: " + amount);
        }
        store.withdraw(from, amount);
        store.deposit(to, amount);
    }

    @Transactional(readOnly = true)
    public int balanceOf(String id) {
        return store.balanceOf(id);
    }
}

// 口座の残高を持つ「DB の代わり」(完成済み。変更しないでください)
class AccountStore {
    private final Map<String, Integer> balances = new HashMap<>();

    void open(String id, int balance) {
        balances.put(id, balance);
    }

    int balanceOf(String id) {
        Integer balance = balances.get(id);
        if (balance == null) {
            throw new IllegalArgumentException("口座がありません: " + id);
        }
        return balance;
    }

    void withdraw(String id, int amount) {
        int balance = balanceOf(id);
        if (balance < amount) {
            throw new IllegalStateException("残高不足です: " + id);
        }
        balances.put(id, balance - amount);
    }

    void deposit(String id, int amount) {
        balances.put(id, balanceOf(id) + amount);
    }

    // ↓ ロールバックの再現用(採点用のトランザクションマネージャが使う)
    Map<String, Integer> snapshot() {
        return new HashMap<>(balances);
    }

    void restore(Map<String, Integer> snapshot) {
        balances.clear();
        balances.putAll(snapshot);
    }
}

