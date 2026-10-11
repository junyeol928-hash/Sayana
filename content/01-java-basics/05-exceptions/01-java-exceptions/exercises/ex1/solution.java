public class QuantityParser {

    static int parseOrDefault(String text, int defaultValue) {
        if (text == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static void main(String[] args) {
        System.out.println(parseOrDefault("12", 1));   // 12
        System.out.println(parseOrDefault(" 7 ", 1));  // 7
        System.out.println(parseOrDefault("12a", 1));  // 1
        System.out.println(parseOrDefault(null, 1));   // 1
    }
}
