import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankAccountTest {

    @Test
    @DisplayName("2つのコンストラクタで名義と残高が設定される")
    void constructors() {
        BankAccount a = new BankAccount("ハル", 5000);
        assertEquals("ハル", a.owner, "owner が設定されていません。this.owner = owner; を確認してください");
        assertEquals(5000, a.balance, "初期残高が設定されていません。this.balance = balance; を確認してください");

        BankAccount b = new BankAccount("モカ");
        assertEquals("モカ", b.owner, "引数1つのコンストラクタでも owner を設定してください(this(owner, 0) を呼ぶ)");
        assertEquals(0, b.balance, "引数1つのコンストラクタでは残高 0 で作ってください");
    }

    @Test
    @DisplayName("入金:正の金額は成功、0円・マイナスは失敗して残高そのまま")
    void deposit() {
        BankAccount a = new BankAccount("ハル", 1000);
        assertTrue(a.deposit(500), "500円の入金は成功(true)のはずです");
        assertEquals(1500, a.balance, "1000円に500円入金したら残高は1500円のはずです");
        assertFalse(a.deposit(0), "0円の入金は失敗(false)にしてください");
        assertFalse(a.deposit(-100), "マイナスの入金は失敗(false)にしてください");
        assertEquals(1500, a.balance, "入金に失敗したときは残高を変えないでください");
    }

    @Test
    @DisplayName("出金:残高ちょうどは成功、1円でも超えたら失敗")
    void withdraw() {
        BankAccount a = new BankAccount("ハル", 3000);
        assertFalse(a.withdraw(3001), "残高3000円から3001円は出金できない(false)はずです");
        assertEquals(3000, a.balance, "出金に失敗したときは残高を変えないでください");
        assertTrue(a.withdraw(3000), "残高ちょうどの3000円は出金できる(true)はずです。> と >= を確認してください");
        assertEquals(0, a.balance, "3000円から3000円出金したら残高は0円のはずです");
        assertFalse(a.withdraw(0), "0円の出金は失敗(false)にしてください");
    }

    @Test
    @DisplayName("振込:自分の残高が減り、相手の残高が増える")
    void transfer() {
        BankAccount haru = new BankAccount("ハル", 10000);
        BankAccount moka = new BankAccount("モカ");
        assertTrue(haru.transferTo(moka, 3000), "残高内の振込は成功(true)のはずです");
        assertEquals(7000, haru.balance, "振込元の残高は 10000 - 3000 = 7000 になるはずです");
        assertEquals(3000, moka.balance, "振込先の残高が増えていません。to.deposit(...) を呼んでいるか確認してください");
    }

    @Test
    @DisplayName("振込:残高不足や振込先 null なら失敗し、どちらの残高も変わらない")
    void transferFails() {
        BankAccount haru = new BankAccount("ハル", 1000);
        BankAccount moka = new BankAccount("モカ", 500);
        assertFalse(haru.transferTo(moka, 1001), "残高不足の振込は失敗(false)のはずです");
        assertEquals(1000, haru.balance, "振込に失敗したら振込元の残高は変えないでください");
        assertEquals(500, moka.balance, "振込に失敗したら振込先に入金しないでください(出金の成否を確認してから入金)");
        assertFalse(haru.transferTo(null, 100), "振込先が null なら false を返してください(NullPointerException にしない)");
        assertEquals(1000, haru.balance, "振込先が null のときは出金しないでください");
    }
}
