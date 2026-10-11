import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    /** 期待値:SHA-256(ソルト + パスワード) を1回目とし、合計 1000 回 */
    private static byte[] expectedDigest(String raw, byte[] salt) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt);
        byte[] h = md.digest(raw.getBytes(StandardCharsets.UTF_8));
        for (int i = 1; i < 1000; i++) {
            h = md.digest(h);
        }
        return h;
    }

    @Test
    @DisplayName("digest:ソルト→パスワードの順に入れ、合計 1000 回 SHA-256 をかける")
    void digest() throws Exception {
        byte[] salt = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        byte[] once = MessageDigest.getInstance("SHA-256")
                .digest(("0123456789abcdef" + "haru-pass").getBytes(StandardCharsets.UTF_8));
        byte[] actual = PasswordHasher.digest("haru-pass", salt);
        assertNotEquals(Base64.getEncoder().encodeToString(once), Base64.getEncoder().encodeToString(actual),
                "1回しかハッシュ化していません。結果をさらに md.digest にかけ、合計 ITERATIONS 回にしてください");
        assertArrayEquals(expectedDigest("haru-pass", salt), actual,
                "ハッシュ値が期待と違います。md.update(salt) → md.digest(パスワードの UTF-8) で1回目、その結果を digest し直して合計 1000 回か確認してください");
    }

    @Test
    @DisplayName("encode:同じパスワードでも毎回ソルトが違うので結果が変わる")
    void encodeUsesRandomSalt() {
        PasswordHasher h = new PasswordHasher();
        String a = h.encode("haru-pass");
        String b = h.encode("haru-pass");
        assertTrue(a.contains("$"), "encode の結果は \"ソルト$ハッシュ\" の形のはずです。encode(raw, salt) を呼んで返していますか?");
        assertNotEquals(a, b, "同じパスワードを2回 encode して同じ結果になりました。毎回 SecureRandom で新しいソルトを作ってください");
        String saltPart = a.substring(0, a.indexOf('$'));
        assertEquals(16, Base64.getDecoder().decode(saltPart).length, "ソルトは SALT_BYTES(16)バイトにしてください");
    }

    @Test
    @DisplayName("matches:正しいパスワードなら true")
    void matchesCorrect() {
        PasswordHasher h = new PasswordHasher();
        String stored = h.encode("haru-pass");
        assertTrue(h.matches("haru-pass", stored),
                "正しいパスワードで false になりました。保存値の「ソルト」を使って計算し直していますか? split(\"$\") は正規表現の行末なので分割できません");
        String fixed = PasswordHasher.encode("mocha-pass", new byte[16]);
        assertTrue(h.matches("mocha-pass", fixed), "ソルトがすべて 0 の保存値でも、正しいパスワードなら true です");
    }

    @Test
    @DisplayName("matches:1文字でも違えば false(大文字小文字も区別)")
    void matchesWrong() {
        PasswordHasher h = new PasswordHasher();
        String stored = h.encode("haru-pass");
        assertFalse(h.matches("Haru-pass", stored), "大文字小文字が違うパスワードは false です");
        assertFalse(h.matches("haru-pas", stored), "1文字足りないパスワードは false です");
        assertFalse(h.matches("", stored), "空文字のパスワードは false です");
    }

    @Test
    @DisplayName("matches:壊れた保存値や null でも例外にならず false")
    void matchesBroken() {
        PasswordHasher h = new PasswordHasher();
        assertDoesNotThrow(() -> h.matches("haru-pass", "abc"), "\"$\" の無い保存値で例外が出ています。false を返してください");
        assertFalse(h.matches("haru-pass", "abc"), "\"$\" の無い保存値なら false です");
        assertFalse(h.matches("haru-pass", "$"), "ソルトもハッシュも空なら false です");
        assertFalse(h.matches("haru-pass", "!!!$???"), "Base64 として不正な保存値なら false です(IllegalArgumentException を catch)");
        assertFalse(h.matches("haru-pass", null), "保存値が null なら false です");
        assertFalse(h.matches(null, h.encode("x")), "入力パスワードが null なら false です");
    }
}
