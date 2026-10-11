public class LeapYear {

    // year がうるう年なら true を返す
    public static boolean isLeapYear(int year) {
        // 「4で割り切れて100で割り切れない」または「400で割り切れる」
        // && は || より優先度が高いが、読みやすさのために括弧を付ける
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }

    public static void main(String[] args) {
        System.out.println("2024: " + isLeapYear(2024));
        System.out.println("1900: " + isLeapYear(1900));
        System.out.println("2000: " + isLeapYear(2000));
    }
}
