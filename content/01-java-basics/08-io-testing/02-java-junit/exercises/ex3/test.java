import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TicketCasesTest {

    /** 仕様どおりの正しい実装 */
    static int price(int age) {
        if (age < 0) {
            return -1;
        }
        if (age <= 5) {
            return 0;
        }
        if (age <= 12) {
            return 1000;
        }
        if (age <= 64) {
            return 1900;
        }
        return 1300;
    }

    /** 各境界の値を指定して実装を作る。正しい値は (0, 5, 12, 64) で、1つだけずらすとミュータントになる */
    static IntUnaryOperator mutant(int minAge, int free, int child, int adult) {
        return age -> {
            if (age < minAge) {
                return -1;
            }
            if (age <= free) {
                return 0;
            }
            if (age <= child) {
                return 1000;
            }
            if (age <= adult) {
                return 1900;
            }
            return 1300;
        };
    }

    /** わざとバグを1か所だけ入れた実装(ミュータント)たち */
    static Map<String, IntUnaryOperator> mutants() {
        Map<String, IntUnaryOperator> m = new LinkedHashMap<>();
        m.put("0歳をエラーにしてしまうバグ(age < 1)", mutant(1, 5, 12, 64));
        m.put("負の年齢をエラーにしないバグ(年齢チェック忘れ)", mutant(Integer.MIN_VALUE, 5, 12, 64));
        m.put("5歳を有料にしてしまうバグ(age <= 4)", mutant(0, 4, 12, 64));
        m.put("6歳を無料にしてしまうバグ(age <= 6)", mutant(0, 6, 12, 64));
        m.put("12歳を一般料金にしてしまうバグ(age <= 11)", mutant(0, 5, 11, 64));
        m.put("13歳を子ども料金にしてしまうバグ(age <= 13)", mutant(0, 5, 13, 64));
        m.put("64歳をシニア料金にしてしまうバグ(age <= 63)", mutant(0, 5, 12, 63));
        m.put("65歳を一般料金にしてしまうバグ(age <= 65)", mutant(0, 5, 12, 65));
        return m;
    }

    private static int[][] cases() {
        int[][] cases = TicketCases.boundaryCases();
        assertNotNull(cases, "boundaryCases() が null を返しています。new int[][] { ... } を返してください");
        for (int i = 0; i < cases.length; i++) {
            assertNotNull(cases[i], (i + 1) + "番目のケースが null です");
            assertEquals(2, cases[i].length,
                    (i + 1) + "番目のケースの要素数が " + cases[i].length + " 個です。{年齢, 期待する料金} の2個にしてください");
        }
        return cases;
    }

    private static Set<Integer> ages() {
        Set<Integer> ages = new HashSet<>();
        for (int[] c : cases()) {
            ages.add(c[0]);
        }
        return ages;
    }

    @Test
    @DisplayName("各ケースが {年齢, 期待する料金} の形になっている")
    void shape() {
        int[][] cases = cases();
        assertTrue(cases.length >= 1, "ケースが1つもありません");
    }

    @Test
    @DisplayName("期待値がすべて仕様どおり")
    void expectedValuesAreCorrect() {
        for (int[] c : cases()) {
            assertEquals(price(c[0]), c[1],
                    c[0] + "歳の期待値が " + c[1] + " になっていますが、仕様では " + price(c[0])
                            + " です。料金表を見直してください(テストの期待値が間違っていると、正しいコードが赤になります)");
        }
    }

    @Test
    @DisplayName("料金が切り替わる境目の両側(5と6、12と13、64と65)を含む")
    void containsBoundaries() {
        Set<Integer> ages = ages();
        for (int[] pair : new int[][] {{5, 6}, {12, 13}, {64, 65}}) {
            assertTrue(ages.contains(pair[0]) && ages.contains(pair[1]),
                    pair[0] + "歳と" + pair[1] + "歳の境目について、両側のケースがそろっていません。"
                            + "境界値テストでは境目の「両側」を入れます");
        }
    }

    @Test
    @DisplayName("最小の正常値 0 歳と、エラーになる負の年齢を含む")
    void containsEdges() {
        Set<Integer> ages = ages();
        assertTrue(ages.contains(0), "0 歳(正常な年齢の最小値)のケースがありません");
        assertTrue(ages.stream().anyMatch(a -> a < 0),
                "負の年齢(例:-1)のケースがありません。異常な入力もテストしましょう");
    }

    @Test
    @DisplayName("わざとバグを入れた8種類の実装(ミュータント)をすべて見つけられる")
    void killsAllMutants() {
        int[][] cases = cases();
        for (Map.Entry<String, IntUnaryOperator> m : mutants().entrySet()) {
            boolean found = false;
            for (int[] c : cases) {
                if (m.getValue().applyAsInt(c[0]) != c[1]) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                fail("「" + m.getKey() + "」を見つけられるケースがありません。このバグがあっても、あなたのテストは全部緑になってしまいます");
            }
        }
    }
}
