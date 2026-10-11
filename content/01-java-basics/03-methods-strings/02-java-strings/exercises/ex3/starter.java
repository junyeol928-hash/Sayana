public class ReportLine {

    // 左寄せ:右側を空白で埋めて幅 width に。長ければ切り詰める
    public static String padRight(String s, int width) {
        // TODO: s が null なら "" として扱う
        // TODO: s.length() が width 以上なら、先頭 width 文字を返す
        // TODO: 足りない分の空白を右に付けて返す(" ".repeat(n) が便利)
        return ""; // ← ダミー。書き換えてください
    }

    // 右寄せ:左側を空白で埋めて幅 width に。長くても切り詰めない
    public static String padLeft(String s, int width) {
        // TODO: s が null なら "" として扱う
        // TODO: s.length() が width 以上なら、そのまま返す
        // TODO: 足りない分の空白を左に付けて返す
        return ""; // ← ダミー。書き換えてください
    }

    // 帳票の1行:商品名(左寄せ12) + 数量(右寄せ5) + 金額(カンマ区切り・右寄せ11)
    public static String formatLine(String name, int qty, int amount) {
        // TODO: padRight と padLeft を呼び出して組み立てる
        // TODO: 金額は String.format("%,d", amount) で 3桁区切りに
        return ""; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        System.out.println("[" + padRight("abc", 6) + "]");      // [abc   ]
        System.out.println("[" + padLeft("42", 5) + "]");        // [   42]
        System.out.println("[" + formatLine("Coffee", 3, 3600) + "]");
        // [Coffee          3      3,600]
    }
}
