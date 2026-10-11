// 注文が不正なことを表す独自の検査例外
class InvalidOrderException extends Exception {
    InvalidOrderException(String message) {
        // TODO: 親クラス(Exception)のコンストラクタに message を渡す
    }
}

public class OrderValidator {

    static void validate(String itemCode, int quantity) throws InvalidOrderException {
        // TODO: itemCode が null または空白だけなら "商品コードが空です" で例外を投げる

        // TODO: quantity が 1〜99 の範囲外なら "数量は1〜99で指定してください: " + quantity で例外を投げる
    }

    static String tryOrder(String itemCode, int quantity) {
        // TODO: validate を呼び、問題なければ "受付: " + itemCode + " × " + quantity を返す
        // TODO: InvalidOrderException を catch したら "エラー: " + メッセージ を返す
        return "";
    }

    public static void main(String[] args) {
        System.out.println(tryOrder("A-100", 3));    // 受付: A-100 × 3
        System.out.println(tryOrder("  ", 3));       // エラー: 商品コードが空です
        System.out.println(tryOrder("A-100", 100));  // エラー: 数量は1〜99で指定してください: 100
    }
}
