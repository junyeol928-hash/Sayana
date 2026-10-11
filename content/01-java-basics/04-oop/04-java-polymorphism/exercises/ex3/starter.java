public class NotificationSender {

    static String[] renderAll(Notification[] list) {
        String[] result = new String[list.length];
        // TODO: 各通知の render() の結果を result に入れる(種類の判定は不要)
        return result;
    }

    static String smsPhones(Notification[] list) {
        // TODO: SMS の通知だけ電話番号を "," 区切りでつなげて返す
        //       if (n instanceof SmsNotification sms) { ... sms.getPhone() ... }
        return "";
    }

    public static void main(String[] args) {
        Notification[] list = {
            new EmailNotification("hal@example.com", "面談のご案内", "明日10時に伺います"),
            new SmsNotification("090-1111-2222", "明日10時に伺います"),
        };
        for (String text : renderAll(list)) {
            System.out.println(text);
            System.out.println("----");
        }
        System.out.println(smsPhones(list)); // 090-1111-2222
    }
}

class Notification {
    private final String message;

    Notification(String message) {
        this.message = message;
    }

    String getMessage() {
        return message;
    }

    String render() {
        return message;
    }
}

class EmailNotification extends Notification {
    private final String address;
    private final String subject;

    EmailNotification(String address, String subject, String message) {
        super(message);
        this.address = address;
        this.subject = subject;
    }

    String getAddress() {
        return address;
    }

    // TODO: render() をオーバーライドし、"宛先: アドレス\n件名: 件名\n本文" を返す
}

class SmsNotification extends Notification {
    static final int MAX_LENGTH = 70;
    private final String phone;

    SmsNotification(String phone, String message) {
        super(message);
        this.phone = phone;
    }

    String getPhone() {
        return phone;
    }

    // TODO: render() をオーバーライドし、"[SMS] " + 本文 を返す
    //       本文が MAX_LENGTH 文字を超えるなら、先頭 69 文字 + "…" にする
}
