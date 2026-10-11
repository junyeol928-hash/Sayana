import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LogMaskerTest {

    @Test
    @DisplayName("maskEmail:先頭1文字と @ 以降だけ残す")
    void maskEmail() {
        assertEquals("t***@example.com", LogMasker.maskEmail("taro.yamada@example.com"),
                "先頭1文字 + *** + @以降 の形にしてください");
        assertEquals("a***@example.jp", LogMasker.maskEmail("a@example.jp"),
                "@ の前が1文字だけでも、先頭1文字 + *** + @以降 にします");
    }

    @Test
    @DisplayName("maskEmail:null・@ が無い・@ で始まるなら *** だけ")
    void maskEmailInvalid() {
        assertEquals("***", LogMasker.maskEmail(null), "null のときは *** を返してください(NullPointerException にしない)");
        assertEquals("***", LogMasker.maskEmail("taro.yamada"), "@ が無いときは、全体を伏せて *** を返してください");
        assertEquals("***", LogMasker.maskEmail("@example.com"), "@ で始まるときは *** を返してください");
    }

    @Test
    @DisplayName("maskCard:ハイフン・空白を除いて末尾4桁だけ残す")
    void maskCard() {
        assertEquals("************1234", LogMasker.maskCard("4111-1111-1111-1234"),
                "ハイフンを取り除いた16桁のうち、末尾4桁以外を * にしてください");
        assertEquals("************1234", LogMasker.maskCard("4111 1111 1111 1234"),
                "空白も取り除いてからマスクしてください");
        assertEquals("*1234", LogMasker.maskCard("51234"),
                "5桁なら先頭1桁だけ * になります");
    }

    @Test
    @DisplayName("maskCard:4桁以下や null は **** だけ(境界値)")
    void maskCardShort() {
        assertEquals("****", LogMasker.maskCard("1234"), "4桁ちょうどのときは、全部伏せて **** を返してください");
        assertEquals("****", LogMasker.maskCard("12-3"), "ハイフンを除いて4桁以下なら **** です");
        assertEquals("****", LogMasker.maskCard(null), "null のときは **** を返してください");
    }

    @Test
    @DisplayName("loginMessage:メールはマスク、パスワードは [MASKED] で一切出さない")
    void loginMessage() {
        String msg = LogMasker.loginMessage("u001", "taro.yamada@example.com", "himitsu123");
        assertFalse(msg.contains("himitsu123"), "ログにパスワードがそのまま出ています!絶対に出さないでください: " + msg);
        assertFalse(msg.contains("taro.yamada"), "ログにメールアドレスがそのまま出ています。maskEmail を使ってください: " + msg);
        assertEquals("ログイン試行 user=u001 email=t***@example.com password=[MASKED]", msg,
                "メッセージの形式が違います。空白の位置や「=」も含めて例のとおりにしてください");
    }

    @Test
    @DisplayName("loginMessage:パスワードの長さも漏らさない")
    void loginMessageLength() {
        String shortPw = LogMasker.loginMessage("u002", "hana@example.com", "abc");
        String longPw = LogMasker.loginMessage("u002", "hana@example.com", "abcdefghijklmnop");
        assertEquals(shortPw, longPw,
                "パスワードの長さによってメッセージが変わっています。*** を長さ分並べるのではなく、固定の [MASKED] にしてください");
    }
}
