import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

record Inquiry(String name, String message) {
}

@Controller
@RequestMapping("/contact")
public class ContactController {

    /** 受け付けた問い合わせ(本物のアプリなら DB に保存する) */
    final List<Inquiry> inquiries = new ArrayList<>();

    @GetMapping
    public String form() {
        return "contact/form";
    }

    @PostMapping
    public String submit(@RequestParam String name, @RequestParam String message) {
        inquiries.add(new Inquiry(name, message));
        return "redirect:/contact/thanks";
    }

    @GetMapping("/thanks")
    public String thanks() {
        return "contact/thanks";
    }
}
