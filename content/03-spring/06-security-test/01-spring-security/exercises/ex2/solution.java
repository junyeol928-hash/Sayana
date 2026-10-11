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
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return encode(raw, salt);
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
        md.update(salt);
        byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
        for (int i = 1; i < ITERATIONS; i++) {
            hash = md.digest(hash);
        }
        return hash;
    }

    /** raw が保存値 stored と同じパスワードか。形式が壊れていれば false */
    public boolean matches(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        int sep = stored.indexOf('$');
        if (sep <= 0 || sep == stored.length() - 1) {
            return false;
        }
        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getDecoder().decode(stored.substring(0, sep));
            expected = Base64.getDecoder().decode(stored.substring(sep + 1));
        } catch (IllegalArgumentException e) {
            return false; // Base64 として不正
        }
        // 保存値のソルトで計算し直して比べる。比較は時間が一定の isEqual で
        return MessageDigest.isEqual(digest(raw, salt), expected);
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
