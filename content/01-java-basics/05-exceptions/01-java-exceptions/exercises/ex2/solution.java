// 注文が不正なことを表す独自の検査例外
class InvalidOrderException extends Exception {
    InvalidOrderException(String message) {
        super(message);
    }
}

public class OrderValidator {

    static void validate(String itemCode, int quantity) throws InvalidOrderException {
        if (itemCode == null || itemCode.isBlank()) {
            throw new InvalidOrderException("商品コードが空です");
        }
        if (quantity < 1 || quantity > 99) {
            throw new InvalidOrderException("数量は1〜99で指定してください: " + quantity);
        }
    }

    static String tryOrder(String itemCode, int quantity) {
        try {
            validate(itemCode, quantity);
            return "受付: " + itemCode + " × " + quantity;
        } catch (InvalidOrderException e) {
            return "エラー: " + e.getMessage();
        }
    }

    public static void main(String[] args) {
        System.out.println(tryOrder("A-100", 3));    // 受付: A-100 × 3
        System.out.println(tryOrder("  ", 3));       // エラー: 商品コードが空です
        System.out.println(tryOrder("A-100", 100));  // エラー: 数量は1〜99で指定してください: 100
    }
}
