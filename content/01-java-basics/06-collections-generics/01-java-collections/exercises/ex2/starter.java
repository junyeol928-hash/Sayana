import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MailingList {

    public static List<String> normalize(List<String> emails) {
        // TODO: emails が null なら空のリストを返す

        // TODO: 「追加順を保つ Set」を用意する

        // TODO: 1件ずつ、null・空白だけなら読み飛ばし、strip() と toLowerCase() で整えて Set に追加する

        // TODO: Set を新しい List に詰め替えて返す
        return new ArrayList<>(); // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        List<String> emails = List.of("b@x.jp", " A@x.jp ", "a@x.jp", "B@X.JP", "c@x.jp");
        System.out.println(normalize(emails)); // [b@x.jp, a@x.jp, c@x.jp]
    }
}
