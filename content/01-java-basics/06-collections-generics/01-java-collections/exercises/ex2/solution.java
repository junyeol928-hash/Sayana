import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MailingList {

    public static List<String> normalize(List<String> emails) {
        if (emails == null) {
            return new ArrayList<>();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String e : emails) {
            if (e == null || e.isBlank()) {
                continue;
            }
            unique.add(e.strip().toLowerCase());
        }
        return new ArrayList<>(unique);
    }

    public static void main(String[] args) {
        List<String> emails = List.of("b@x.jp", " A@x.jp ", "a@x.jp", "B@X.JP", "c@x.jp");
        System.out.println(normalize(emails)); // [b@x.jp, a@x.jp, c@x.jp]
    }
}
