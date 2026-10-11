import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * あなたの書いた check(テスト)を、正しい実装と「わざとバグを入れた実装」にかけて採点する。
 * 良いテストは、正しい実装では通り、バグ入りの実装では落ちる。
 */
class ShipApiCheckTest {

    /** 送料の決め方(実装ごとに差し替える) */
    interface FeeRule {
        ResponseEntity<Map<String, Object>> apply(int amount, String rank);
    }

    @RestController
    static class RuleFeeController {
        private final FeeRule rule;

        RuleFeeController(FeeRule rule) {
            this.rule = rule;
        }

        @GetMapping("/shipping-fee")
        public ResponseEntity<Map<String, Object>> fee(@RequestParam("amount") int amount,
                @RequestParam(value = "rank", defaultValue = "NORMAL") String rank) {
            return rule.apply(amount, rank);
        }
    }

    private static ResponseEntity<Map<String, Object>> ok(int amount, int fee) {
        return ResponseEntity.ok(Map.of("amount", amount, "fee", fee));
    }

    private static ResponseEntity<Map<String, Object>> badRequest() {
        return ResponseEntity.badRequest().build();
    }

    private static MockMvc mvc(FeeRule rule) {
        return MockMvcBuilders.standaloneSetup(new RuleFeeController(rule)).build();
    }

    /** 仕様どおりの実装 */
    private static final FeeRule CORRECT = (amount, rank) -> {
        if (amount <= 0) {
            return badRequest();
        }
        return ok(amount, ("GOLD".equals(rank) || amount >= 5000) ? 0 : 500);
    };

    @Test
    @DisplayName("正しい実装に対しては、check は何も投げずに通る")
    void passesCorrect() {
        assertDoesNotThrow(() -> ShipApiCheck.check(mvc(CORRECT)),
                "仕様どおりの実装なのに check が失敗しました。期待値(ステータス・fee の値・パラメータ名 amount / rank)が仕様と合っているか確認してください");
    }

    @Test
    @DisplayName("バグ1:5000 円ちょうどを有料にしてしまう(> と >= の取り違え)を見つける")
    void catchesBoundaryBug() {
        FeeRule bug = (amount, rank) -> {
            if (amount <= 0) {
                return badRequest();
            }
            return ok(amount, ("GOLD".equals(rank) || amount > 5000) ? 0 : 500);
        };
        assertThrows(AssertionError.class, () -> ShipApiCheck.check(mvc(bug)),
                "5000 円ちょうどで送料 500 円になるバグを見逃しました。amount=5000 で fee が 0 になることを検証してください(境界値!)");
    }

    @Test
    @DisplayName("バグ2:GOLD 会員を特別扱いしていないのを見つける")
    void catchesGoldBug() {
        FeeRule bug = (amount, rank) -> {
            if (amount <= 0) {
                return badRequest();
            }
            return ok(amount, amount >= 5000 ? 0 : 500);
        };
        assertThrows(AssertionError.class, () -> ShipApiCheck.check(mvc(bug)),
                "GOLD 会員でも送料を取るバグを見逃しました。.param(\"rank\", \"GOLD\") で少額(例: 100 円)のとき fee が 0 になることを検証してください");
    }

    @Test
    @DisplayName("バグ3:金額 0 円を受け付けてしまうのを見つける")
    void catchesValidationBug() {
        FeeRule bug = (amount, rank) -> ok(amount, ("GOLD".equals(rank) || amount >= 5000) ? 0 : 500);
        assertThrows(AssertionError.class, () -> ShipApiCheck.check(mvc(bug)),
                "金額 0 円でも 200 を返すバグを見逃しました。amount=0 で status().isBadRequest() になることを検証してください");
    }

    @Test
    @DisplayName("バグ4:有料のときの送料額が間違っているのを見つける")
    void catchesWrongFeeBug() {
        FeeRule bug = (amount, rank) -> {
            if (amount <= 0) {
                return badRequest();
            }
            return ok(amount, ("GOLD".equals(rank) || amount >= 5000) ? 0 : 300);
        };
        assertThrows(AssertionError.class, () -> ShipApiCheck.check(mvc(bug)),
                "送料が 300 円になっているバグを見逃しました。5000 円未満の一般会員(例: 4999 円)で fee が 500 になることを検証してください");
    }
}
