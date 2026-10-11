import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeAccountTest {

    @Test
    @DisplayName("フィールド balance が private になっている")
    void balanceIsPrivate() throws Exception {
        Field f = SafeAccount.class.getDeclaredField("balance");
        assertTrue(Modifier.isPrivate(f.getModifiers()),
                "balance に private が付いていません。外から acc.balance = -1 と書けてしまいます");
    }

    @Test
    @DisplayName("初期残高 0 は OK、マイナスなら IllegalArgumentException")
    void constructor() {
        assertEquals(0, new SafeAccount(0).getBalance(), "初期残高 0 は正しい値なので、そのまま作れるはずです");
        assertEquals(1000, new SafeAccount(1000).getBalance(), "初期残高 1000 で作ったら getBalance() は 1000 のはずです");
        assertThrows(IllegalArgumentException.class, () -> new SafeAccount(-1),
                "初期残高がマイナス(-1)なら IllegalArgumentException を投げてください");
    }

    @Test
    @DisplayName("入金で残高が増える。0円以下の入金は IllegalArgumentException")
    void deposit() {
        SafeAccount acc = new SafeAccount(100);
        acc.deposit(50);
        assertEquals(150, acc.getBalance(), "100円に50円入金したら150円のはずです");
        assertThrows(IllegalArgumentException.class, () -> acc.deposit(0),
                "0円の入金は IllegalArgumentException を投げてください");
        assertThrows(IllegalArgumentException.class, () -> acc.deposit(-10),
                "マイナスの入金は IllegalArgumentException を投げてください");
        assertEquals(150, acc.getBalance(), "入金を拒否したときは残高を変えないでください");
    }

    @Test
    @DisplayName("残高不足の出金は false で残高そのまま、残高ちょうどは成功")
    void withdraw() {
        SafeAccount acc = new SafeAccount(1000);
        assertFalse(acc.withdraw(1001), "残高1000円から1001円は出金できない(false)はずです");
        assertEquals(1000, acc.getBalance(), "出金に失敗したら残高は変えないでください(マイナスにしない)");
        assertTrue(acc.withdraw(1000), "残高ちょうど1000円の出金は成功(true)のはずです");
        assertEquals(0, acc.getBalance(), "1000円から1000円出金したら残高は0円のはずです");
    }

    @Test
    @DisplayName("0円以下の出金は IllegalArgumentException")
    void withdrawInvalid() {
        SafeAccount acc = new SafeAccount(1000);
        assertThrows(IllegalArgumentException.class, () -> acc.withdraw(0),
                "0円の出金は IllegalArgumentException を投げてください");
        assertThrows(IllegalArgumentException.class, () -> acc.withdraw(-500),
                "マイナスの出金は IllegalArgumentException を投げてください(残高が増えてしまいます)");
        assertEquals(1000, acc.getBalance(), "出金を拒否したときは残高を変えないでください");
    }
}
