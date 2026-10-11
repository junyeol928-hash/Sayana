public final class Money {
    private final int amount;
    private final String currency;

    Money(int amount, String currency) {
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "金額は0以上: " + amount);
        }
        if (currency == null || currency.isEmpty()) {
            throw new IllegalArgumentException("通貨が未指定です");
        }
        this.amount = amount;
        this.currency = currency;
    }

    int getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    Money plus(Money other) {
        if (other == null || !currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "同じ通貨どうししか足せません");
        }
        return new Money(amount + other.amount, currency);
    }

    Money times(int n) {
        if (n < 0) {
            throw new IllegalArgumentException(
                    "倍率は0以上: " + n);
        }
        return new Money(amount * n, currency);
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }

    public static void main(String[] args) {
        Money price = new Money(1200, "JPY");
        Money total = price.times(3).plus(new Money(500, "JPY"));
        System.out.println(total); // 4100 JPY
        System.out.println(price); // 1200 JPY
    }
}
