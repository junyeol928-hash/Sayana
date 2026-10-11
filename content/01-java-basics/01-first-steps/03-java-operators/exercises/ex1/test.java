import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WarikanTest {

    @Test
    @DisplayName("10000円を3人で割ると1人3333円、端数1円")
    void notDivisible() {
        assertEquals(3333, Warikan.perPerson(10000, 3),
                "10000 ÷ 3 の1人あたりは 3333 円です。整数の割り算 / を使っていますか?");
        assertEquals(1, Warikan.remainder(10000, 3),
                "10000 ÷ 3 の端数は 1 円です。余りを求める演算子 % を使っていますか?");
    }

    @Test
    @DisplayName("9000円を3人で割ると割り切れて端数0円")
    void divisible() {
        assertEquals(3000, Warikan.perPerson(9000, 3), "9000 ÷ 3 の1人あたりは 3000 円です");
        assertEquals(0, Warikan.remainder(9000, 3), "割り切れるときの端数は 0 円です");
    }

    @Test
    @DisplayName("金額が人数より少ないときは1人0円、全額が端数")
    void totalLessThanPeople() {
        assertEquals(0, Warikan.perPerson(2, 5), "2円を5人で割ると1人あたり 0 円(切り捨て)です");
        assertEquals(2, Warikan.remainder(2, 5), "2円を5人で割ると端数は 2 円です");
    }

    @Test
    @DisplayName("1人なら全額を1人で払い、端数は0円")
    void onePerson() {
        assertEquals(4321, Warikan.perPerson(4321, 1), "1人で払うときは合計金額そのままです");
        assertEquals(0, Warikan.remainder(4321, 1), "1人で払うときの端数は 0 円です");
    }

    @Test
    @DisplayName("1人あたり × 人数 + 端数 が合計金額に一致する")
    void consistency() {
        int total = 12345;
        int people = 7;
        int sum = Warikan.perPerson(total, people) * people + Warikan.remainder(total, people);
        assertEquals(total, sum,
                "1人あたり × 人数 + 端数 が合計金額 12345 円になっていません。計算を見直してください");
    }
}
