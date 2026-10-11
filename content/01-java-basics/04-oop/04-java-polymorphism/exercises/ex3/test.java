import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationSenderTest {

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(n);
    }

    @Test
    @DisplayName("メールの文面は 宛先・件名・本文 の3行")
    void emailRender() {
        Notification n = new EmailNotification("hal@example.com", "面談のご案内", "明日10時に伺います");
        assertEquals("宛先: hal@example.com\n件名: 面談のご案内\n明日10時に伺います", n.render(),
                "メールの render() は \"宛先: アドレス\\n件名: 件名\\n本文\" です。EmailNotification で render をオーバーライドしましたか?");
    }

    @Test
    @DisplayName("SMS の文面は [SMS] + 本文(70文字ちょうどはそのまま)")
    void smsRenderShort() {
        Notification n = new SmsNotification("090-1111-2222", "明日10時に伺います");
        assertEquals("[SMS] 明日10時に伺います", n.render(),
                "SMS の render() は \"[SMS] \" + 本文 です");
        String seventy = repeat('あ', 70);
        assertEquals("[SMS] " + seventy, new SmsNotification("090", seventy).render(),
                "本文がちょうど 70 文字なら省略しません(境界値。> と >= を確認してください)");
    }

    @Test
    @DisplayName("SMS の本文が 70 文字を超えたら 69 文字 + … に省略")
    void smsRenderLong() {
        String body = repeat('あ', 69) + "いう";  // 71 文字
        String expected = "[SMS] " + repeat('あ', 69) + "…";
        assertEquals(expected, new SmsNotification("090", body).render(),
                "71 文字の本文は、先頭 69 文字 + \"…\" の 70 文字にします");
    }

    @Test
    @DisplayName("renderAll は種類の違う通知を順番どおりに文面化する")
    void renderAll() {
        Notification[] list = {
            new SmsNotification("090-1111-2222", "到着しました"),
            new EmailNotification("a@example.com", "件名", "本文"),
        };
        assertArrayEquals(new String[] { "[SMS] 到着しました", "宛先: a@example.com\n件名: 件名\n本文" },
                NotificationSender.renderAll(list),
                "renderAll は各通知の render() の結果を、同じ順番で配列に入れて返します");
        assertEquals(0, NotificationSender.renderAll(new Notification[0]).length,
                "通知が0件なら、長さ0の配列を返します");
    }

    @Test
    @DisplayName("smsPhones は SMS の電話番号だけを , 区切りで返す")
    void smsPhones() {
        Notification[] list = {
            new EmailNotification("a@example.com", "件名", "本文"),
            new SmsNotification("090-1111-2222", "A"),
            new EmailNotification("b@example.com", "件名", "本文"),
            new SmsNotification("080-3333-4444", "B"),
        };
        assertEquals("090-1111-2222,080-3333-4444", NotificationSender.smsPhones(list),
                "SMS の電話番号だけを登場順に \",\" でつなげます(先頭・末尾に \",\" は付けない)");
    }

    @Test
    @DisplayName("SMS が1件もなければ空文字")
    void smsPhonesNone() {
        Notification[] list = { new EmailNotification("a@example.com", "件名", "本文") };
        assertEquals("", NotificationSender.smsPhones(list),
                "SMS が1件もなければ \"\"(空文字)を返します");
    }
}
