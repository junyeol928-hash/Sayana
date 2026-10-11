public class ReportLine {

    public static String padRight(String s, int width) {
        if (s == null) {
            s = "";
        }
        if (s.length() >= width) {
            return s.substring(0, width);
        }
        return s + " ".repeat(width - s.length());
    }

    public static String padLeft(String s, int width) {
        if (s == null) {
            s = "";
        }
        if (s.length() >= width) {
            return s;
        }
        return " ".repeat(width - s.length()) + s;
    }

    public static String formatLine(String name, int qty, int amount) {
        return padRight(name, 12)
                + padLeft(String.valueOf(qty), 5)
                + padLeft(String.format("%,d", amount), 11);
    }

    public static void main(String[] args) {
        System.out.println("[" + padRight("abc", 6) + "]");      // [abc   ]
        System.out.println("[" + padLeft("42", 5) + "]");        // [   42]
        System.out.println("[" + formatLine("Coffee", 3, 3600) + "]");
        // [Coffee          3      3,600]
    }
}
