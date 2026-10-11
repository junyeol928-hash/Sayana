public class EmployeeId {

    // 社員番号が「EMP- + 数字5桁」の形式なら true
    public static boolean isValid(String id) {
        // TODO: null なら false
        // TODO: strip() で前後の空白(全角含む)を除く
        // TODO: 長さが 9 でなければ false
        // TODO: 先頭4文字が "EMP-"(大文字小文字は無視)でなければ false
        // TODO: 残り5文字がすべて '0'〜'9' なら true
        return false; // ← ダミー。書き換えてください
    }

    // 正しい形式なら "EMP-" を大文字にした社員番号、そうでなければ null
    public static String normalize(String id) {
        // TODO: isValid でなければ null
        // TODO: strip した文字列の数字部分(5文字)の前に "EMP-" を付けて返す
        return null; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        System.out.println(isValid("EMP-00123"));     // true
        System.out.println(isValid("EMP-0012A"));     // false
        System.out.println(normalize(" emp-00042 ")); // EMP-00042
    }
}
