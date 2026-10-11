import java.util.List;

public class MoneyCalc {

    public static Money total(List<Money> items, String currency) {
        // TODO: new Money(0, currency) から始めて、items を順に add していく
        return null; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        Money a = new Money(500, "JPY");
        Money b = new Money(300, "JPY");
        System.out.println(a.add(b));                     // Money[amount=800, currency=JPY]
        System.out.println(total(List.of(a, b), "JPY"));  // Money[amount=800, currency=JPY]
        System.out.println(total(List.of(), "USD"));      // Money[amount=0, currency=USD]
    }
}

record Money(long amount, String currency) {

    Money {
        // TODO: amount が負なら IllegalArgumentException を投げる
        // TODO: currency が null または空白だけなら IllegalArgumentException を投げる
    }

    Money add(Money other) {
        // TODO: 通貨が違えば IllegalArgumentException
        // TODO: 同じ通貨なら、金額を合計した新しい Money を返す
        return this; // ← ダミー。書き換えてください
    }
}
