import org.springframework.web.bind.annotation.*;

@RestController
public class GreetingController {

    @GetMapping("/hello")
    public String hello(@RequestParam(defaultValue = "ゲスト") String name) {
        return "こんにちは、" + name + "さん!";
    }

    @GetMapping("/members/{id}")
    public String member(@PathVariable int id) {
        return "会員番号: " + id;
    }
}
