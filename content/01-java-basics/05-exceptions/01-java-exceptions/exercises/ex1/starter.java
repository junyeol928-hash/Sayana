public class QuantityParser {

    static int parseOrDefault(String text, int defaultValue) {
        // TODO: text が null なら defaultValue を返す

        // TODO: try の中で、前後の空白を除いて Integer.parseInt する

        // TODO: NumberFormatException を catch したら defaultValue を返す

        return 0; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        System.out.println(parseOrDefault("12", 1));   // 12
        System.out.println(parseOrDefault(" 7 ", 1));  // 7
        System.out.println(parseOrDefault("12a", 1));  // 1
        System.out.println(parseOrDefault(null, 1));   // 1
    }
}
