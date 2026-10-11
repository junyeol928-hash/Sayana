import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

record Inquiry(String name, String message) {
}

// TODO: @Controller と @RequestMapping("/contact") を付ける
public class ContactController {

    /** 受け付けた問い合わせ(本物のアプリなら DB に保存する) */
    final List<Inquiry> inquiries = new ArrayList<>();

    // TODO: GET /contact → ビュー名 "contact/form"
    public String form() {
        return null; // ← ダミー
    }

    // TODO: POST /contact → name と message を受け取って inquiries に追加し、
    //       "redirect:/contact/thanks" を返す
    public String submit(String name, String message) {
        return null; // ← ダミー
    }

    // TODO: GET /contact/thanks → ビュー名 "contact/thanks"
    public String thanks() {
        return null; // ← ダミー
    }
}
