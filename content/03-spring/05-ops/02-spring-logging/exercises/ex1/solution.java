public class LogMasker {

    /** メールアドレスを t***@example.com の形にする */
    public static String maskEmail(String email) {
        if (email == null) {
            return "***";
        }
        int at = email.indexOf("@");
        if (at <= 0) {          // @ が無い(-1)か、@ で始まる(0)
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** カード番号の末尾4桁だけ残す */
    public static String maskCard(String cardNumber) {
        if (cardNumber == null) {
            return "****";
        }
        String digits = cardNumber.replace("-", "").replace(" ", "");
        if (digits.length() <= 4) {
            return "****";
        }
        int hidden = digits.length() - 4;
        return "*".repeat(hidden) + digits.substring(hidden);
    }

    /** ログイン試行のログメッセージを組み立てる */
    public static String loginMessage(String userId, String email, String password) {
        // password は受け取っても使わない(長さすら出さない)
        return "ログイン試行 user=" + userId
                + " email=" + maskEmail(email)
                + " password=[MASKED]";
    }

    public static void main(String[] args) {
        System.out.println(maskEmail("taro.yamada@example.com"));   // t***@example.com
        System.out.println(maskCard("4111-1111-1111-1234"));        // ************1234
        System.out.println(loginMessage("u001", "taro.yamada@example.com", "himitsu123"));
    }
}
