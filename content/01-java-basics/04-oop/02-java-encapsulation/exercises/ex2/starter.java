public final class Money {
    // TODO: 2つのフィールドを private final にする
    int amount;
    String currency;

    Money(int amount, String currency) {
        // TODO: amount が 0 未満なら IllegalArgumentException
        // TODO: currency が null または "" なら IllegalArgumentException
        // TODO: フィールドに代入する
    }

    int getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    Money plus(Money other) {
        // TODO: other が null、または通貨が違うなら IllegalArgumentException
        // TODO: 合計を持つ新しい Money を返す(自分は書き換えない)
        return null; // ← ダミー。書き換えてください
    }

    Money times(int n) {
        // TODO: n が 0 未満なら IllegalArgumentException
        // TODO: 金額を n 倍した新しい Money を返す
        return null; // ← ダミー。書き換えてください
    }

    @Override
    public String toString() {
        // TODO: "1200 JPY" の形式で返す
        return ""; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        Money price = new Money(1200, "JPY");
        Money total = price.times(3).plus(new Money(500, "JPY"));
        System.out.println(total); // 4100 JPY
        System.out.println(price); // 1200 JPY
    }
}
