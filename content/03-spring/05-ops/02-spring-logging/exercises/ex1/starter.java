public class LogMasker {

    /** メールアドレスを t***@example.com の形にする */
    public static String maskEmail(String email) {
        // TODO: null、@ が無い、@ で始まる なら "***"
        // TODO: 先頭1文字 + "***" + @以降
        return email; // ← ダミー(マスクしていない!)。書き換えてください
    }

    /** カード番号の末尾4桁だけ残す */
    public static String maskCard(String cardNumber) {
        // TODO: null なら "****"
        // TODO: ハイフンと空白を取り除き、4桁以下なら "****"
        // TODO: 末尾4桁より前を * にする("*".repeat(n) が便利)
        return cardNumber; // ← ダミー(マスクしていない!)。書き換えてください
    }

    /** ログイン試行のログメッセージを組み立てる */
    public static String loginMessage(String userId, String email, String password) {
        // TODO: "ログイン試行 user=<userId> email=<マスクしたメール> password=[MASKED]"
        return "ログイン試行 user=" + userId + " email=" + email + " password=" + password; // ← 危険なダミー
    }

    public static void main(String[] args) {
        System.out.println(maskEmail("taro.yamada@example.com"));   // t***@example.com
        System.out.println(maskCard("4111-1111-1111-1234"));        // ************1234
        System.out.println(loginMessage("u001", "taro.yamada@example.com", "himitsu123"));
    }
}
