import org.springframework.web.bind.annotation.*;

// TODO: このクラスに @RestController を付ける
public class GreetingController {

    // TODO: GET /hello に対応させる(@GetMapping)
    // TODO: name を @RequestParam で受け取る。無いとき・空のときは "ゲスト"
    public String hello(String name) {
        return ""; // ← ダミー。「こんにちは、○○さん!」を返す
    }

    // TODO: GET /members/{id} に対応させ、id を @PathVariable で受け取る
    public String member(int id) {
        return ""; // ← ダミー。「会員番号: 42」の形で返す
    }
}
