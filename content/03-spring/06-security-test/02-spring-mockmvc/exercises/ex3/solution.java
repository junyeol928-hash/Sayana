import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 送料 API の仕様を確かめる「テスト」を書く */
public class ShipApiCheck {

    /** mvc の先にある送料 API が仕様どおりなら何も起きず、違えば AssertionError を投げる */
    public static void check(MockMvc mvc) throws Exception {
        // 一般会員:5000 円未満は 500 円(境界の内側)
        mvc.perform(get("/shipping-fee").param("amount", "4999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(4999))
                .andExpect(jsonPath("$.fee").value(500));
        // 一般会員:5000 円ちょうどで無料(境界値)
        mvc.perform(get("/shipping-fee").param("amount", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(0));
        // GOLD 会員は少額でも無料
        mvc.perform(get("/shipping-fee").param("amount", "100").param("rank", "GOLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(0));
        // 金額 0 円は不正な入力
        mvc.perform(get("/shipping-fee").param("amount", "0"))
                .andExpect(status().isBadRequest());
    }

    public static void main(String[] args) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FeeController()).build();
        check(mvc);
        System.out.println("検証OK:FeeController は仕様どおりです");
    }
}

// ---- 手元で試すための、仕様どおりの実装(完成済み) ----

record FeeResult(int amount, int fee) { }

@RestController
class FeeController {

    @GetMapping("/shipping-fee")
    public ResponseEntity<FeeResult> fee(@RequestParam("amount") int amount,
            @RequestParam(value = "rank", defaultValue = "NORMAL") String rank) {
        if (amount <= 0) {
            return ResponseEntity.badRequest().build();
        }
        int fee = ("GOLD".equals(rank) || amount >= 5000) ? 0 : 500;
        return ResponseEntity.ok(new FeeResult(amount, fee));
    }
}
