import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * ソルト + ストレッチングでパスワードをハッシュ化する(仕組みの学習用)。
 * 本番では自作せず、Spring Security の BCryptPasswordEncoder などを使うこと。
 */
public class PasswordHasher {

    /** ハッシュ計算を繰り返す回数(ストレッチング) */
    static final int ITERATIONS = 1000;
    /** ソルトのバイト数 */
    static final int SALT_BYTES = 16;

    private final SecureRandom random = new SecureRandom();

    /** 新しいランダムなソルトでハッシュ化し、"ソルト$ハッシュ"(どちらも Base64)で返す */
    public String encode(String raw) {
        // TODO: SALT_BYTES バイトの配列を作り、random.nextBytes で乱数を詰める
        // TODO: encode(raw, salt) を呼んで返す
        return ""; // ← ダミー。書き換えてください
    }

    /** 指定したソルトでハッシュ化し、"ソルト$ハッシュ"(どちらも Base64)で返す(完成済み) */
    static String encode(String raw, byte[] salt) {
        Base64.Encoder b64 = Base64.getEncoder();
        return b64.encodeToString(salt) + "$" + b64.encodeToString(digest(raw, salt));
    }

    /** SHA-256(ソルト + パスワード) を求め、その結果をさらに SHA-256 にかけて、合計 ITERATIONS 回計算する */
    static byte[] digest(String raw, byte[] salt) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 はどの JDK にも必ずある
        }
        // TODO: md.update(salt) でソルトを先に入れ、md.digest(パスワードの UTF-8 バイト列) で1回目のハッシュを得る
        // TODO: その結果を md.digest(...) にかけ直す処理を、合計 ITERATIONS 回になるまで繰り返す
        return new byte[32]; // ← ダミー。書き換えてください
    }

    /** raw が保存値 stored と同じパスワードか。形式が壊れていれば false */
    public boolean matches(String raw, String stored) {
        // TODO: raw や stored が null なら false
        // TODO: stored を "$" の位置でソルト部分とハッシュ部分に分ける
        //       (split("$") は正規表現の「行末」になってしまうので indexOf('$') か split("\\$") を使う)
        //       "$" が無い・前後が空なら false
        // TODO: それぞれ Base64.getDecoder().decode(...) でバイト列に戻す(不正な Base64 なら IllegalArgumentException → false)
        // TODO: digest(raw, ソルト) を計算し直し、MessageDigest.isEqual で保存されたハッシュと比べる
        return false; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        PasswordHasher hasher = new PasswordHasher();
        String a = hasher.encode("haru-pass");
        String b = hasher.encode("haru-pass");
        System.out.println(a);
        System.out.println(b);
        System.out.println(a.equals(b));                      // false(ソルトが違う)
        System.out.println(hasher.matches("haru-pass", a));   // true
        System.out.println(hasher.matches("HARU-pass", a));   // false
    }
}
