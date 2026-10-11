import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeIdTest {

    @Test
    @DisplayName("EMP- + 数字5桁は正しい形式")
    void valid() {
        assertTrue(EmployeeId.isValid("EMP-00123"), "\"EMP-00123\" は正しい社員番号です");
        assertTrue(EmployeeId.isValid("EMP-99999"), "\"EMP-99999\" は正しい社員番号です");
    }

    @Test
    @DisplayName("小文字の emp- や前後の空白(全角スペース含む)は許す")
    void lowerCaseAndSpaces() {
        assertTrue(EmployeeId.isValid("emp-00123"), "先頭の EMP- は大文字小文字を区別しません。equalsIgnoreCase を使いましょう");
        assertTrue(EmployeeId.isValid("  EMP-00123 "), "前後の半角スペースは無視してください");
        assertTrue(EmployeeId.isValid("　EMP-00123　"),
                "前後の全角スペースも無視してください。trim() では全角スペースが消えないので strip() を使います");
    }

    @Test
    @DisplayName("数字が4桁・6桁は不正(境界値)")
    void wrongLength() {
        assertFalse(EmployeeId.isValid("EMP-0123"), "数字4桁(全体8文字)は不正です");
        assertFalse(EmployeeId.isValid("EMP-001234"), "数字6桁(全体10文字)は不正です");
        assertFalse(EmployeeId.isValid(""), "空文字は不正です(長さチェックの前に substring すると例外になります)");
        assertFalse(EmployeeId.isValid("EMP"), "短すぎる文字列は false を返してください(例外にしない)");
    }

    @Test
    @DisplayName("数字以外が混ざる・区切りが違う・null は不正")
    void invalidChars() {
        assertFalse(EmployeeId.isValid("EMP-0012A"), "数字部分に A が混ざっているので不正です。1文字ずつ '0'〜'9' か確認しましょう");
        assertFalse(EmployeeId.isValid("EMP_00123"), "区切りが _ なので不正です");
        assertFalse(EmployeeId.isValid("EMX-00123"), "先頭が EMX- なので不正です");
        assertFalse(EmployeeId.isValid("EMP-00 23"), "数字部分に空白が混ざっているので不正です");
        assertFalse(EmployeeId.isValid(null), "null なら false を返してください(NullPointerException にしない)");
    }

    @Test
    @DisplayName("normalize は空白を除き EMP- を大文字にする。不正なら null")
    void normalize() {
        assertEquals("EMP-00042", EmployeeId.normalize(" emp-00042 "),
                "\" emp-00042 \" は \"EMP-00042\" に整えてください(前後の空白を除き、EMP- を大文字に)");
        assertEquals("EMP-00123", EmployeeId.normalize("EMP-00123"), "正しい形式ならそのままの社員番号を返してください");
        assertNull(EmployeeId.normalize("EMP-0012A"), "不正な社員番号なら null を返してください");
        assertNull(EmployeeId.normalize(null), "null が渡されたら null を返してください");
    }
}
