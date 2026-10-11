public class NotificationSender {

    static String[] renderAll(Notification[] list) {
        String[] result = new String[list.length];
        for (int i = 0; i < list.length; i++) {
            result[i] = list[i].render();
        }
        return result;
    }

    static String smsPhones(Notification[] list) {
        String phones = "";
        for (Notification n : list) {
            if (n instanceof SmsNotification sms) {
                if (!phones.isEmpty()) {
                    phones += ",";
                }
                phones += sms.getPhone();
            }
        }
        return phones;
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

    @Override
    String render() {
        return "宛先: " + address + "\n件名: " + subject + "\n" + getMessage();
    }
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

    @Override
    String render() {
        String body = getMessage();
        if (body.length() > MAX_LENGTH) {
            body = body.substring(0, MAX_LENGTH - 1) + "…";
        }
        return "[SMS] " + body;
    }
}
