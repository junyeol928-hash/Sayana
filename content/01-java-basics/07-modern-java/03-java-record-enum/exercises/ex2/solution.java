import java.util.List;

public class MoneyCalc {

    public static Money total(List<Money> items, String currency) {
        Money sum = new Money(0, currency);
        for (Money m : items) {
            sum = sum.add(m); // add は新しい Money を返すので、必ず受け取り直す
        }
        return sum;
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
        if (amount < 0) {
            throw new IllegalArgumentException("金額が負です: " + amount);
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("通貨が指定されていません");
        }
    }

    Money add(Money other) {
        if (!currency.equals(other.currency())) {
            throw new IllegalArgumentException(
                    "通貨が違います: " + currency + " と " + other.currency());
        }
        return new Money(amount + other.amount(), currency);
    }
}
