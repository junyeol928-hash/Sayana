import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderValidatorTest {

    @Test
    @DisplayName("独自例外はメッセージを保持する")
    void exceptionMessage() {
        InvalidOrderException e = new InvalidOrderException("テスト");
        assertEquals("テスト", e.getMessage(),
                "getMessage() が受け取ったメッセージを返しません。コンストラクタで super(message) を呼んでください");
    }

    @Test
    @DisplayName("数量 1 と 99 は正常(境界値)")
    void validBoundary() {
        assertDoesNotThrow(() -> OrderValidator.validate("A-100", 1),
                "数量 1 は範囲内なので例外を投げてはいけません");
        assertDoesNotThrow(() -> OrderValidator.validate("A-100", 99),
                "数量 99 は範囲内なので例外を投げてはいけません");
    }

    @Test
    @DisplayName("数量 0 と 100 は InvalidOrderException(境界値)")
    void invalidQuantity() {
        InvalidOrderException e0 = assertThrows(InvalidOrderException.class,
                () -> OrderValidator.validate("A-100", 0),
                "数量 0 は範囲外なので InvalidOrderException を投げてください");
        assertEquals("数量は1〜99で指定してください: 0", e0.getMessage(),
                "例外のメッセージの末尾には実際の数量を付けてください");
        assertThrows(InvalidOrderException.class,
                () -> OrderValidator.validate("A-100", 100),
                "数量 100 は範囲外なので InvalidOrderException を投げてください");
    }

    @Test
    @DisplayName("商品コードが null・空白だけなら InvalidOrderException")
    void blankCode() {
        InvalidOrderException e = assertThrows(InvalidOrderException.class,
                () -> OrderValidator.validate(null, 3),
                "商品コードが null なら InvalidOrderException を投げてください(NullPointerException ではなく)");
        assertEquals("商品コードが空です", e.getMessage(), "メッセージは \"商品コードが空です\" です");
        assertThrows(InvalidOrderException.class,
                () -> OrderValidator.validate("   ", 3),
                "空白だけの商品コードも空とみなしてください(isBlank() が使えます)");
    }

    @Test
    @DisplayName("商品コードのチェックが数量より先")
    void checkOrder() {
        InvalidOrderException e = assertThrows(InvalidOrderException.class,
                () -> OrderValidator.validate("", 0),
                "商品コードも数量も不正なら InvalidOrderException を投げてください");
        assertEquals("商品コードが空です", e.getMessage(),
                "両方不正なときは商品コードのエラーが優先です。チェックの順番を確認してください");
    }

    @Test
    @DisplayName("tryOrder は結果を画面用の文字列にする")
    void tryOrder() {
        assertEquals("受付: A-100 × 3", OrderValidator.tryOrder("A-100", 3),
                "正常なら \"受付: 商品コード × 数量\" を返してください");
        assertEquals("エラー: 商品コードが空です", OrderValidator.tryOrder("  ", 3),
                "InvalidOrderException を catch したら \"エラー: \" + getMessage() を返してください");
        assertEquals("エラー: 数量は1〜99で指定してください: 100", OrderValidator.tryOrder("A-100", 100),
                "数量エラーのときも \"エラー: \" + メッセージ を返してください");
    }
}
