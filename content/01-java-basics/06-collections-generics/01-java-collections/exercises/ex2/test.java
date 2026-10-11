import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailingListTest {

    @Test
    @DisplayName("表記ゆれを整えて重複を除き、最初に出てきた順に並べる")
    void basic() {
        List<String> r = MailingList.normalize(
                List.of("b@x.jp", " A@x.jp ", "a@x.jp", "B@X.JP", "c@x.jp"));
        assertEquals(List.of("b@x.jp", "a@x.jp", "c@x.jp"), r,
                "結果が違います。strip() と toLowerCase() で整えてから、追加順を保つ Set(LinkedHashSet)で重複を除いていますか? 実際の結果: " + r);
    }

    @Test
    @DisplayName("並び順は最初に出てきた順(辞書順ではない)")
    void keepsFirstOrder() {
        List<String> r = MailingList.normalize(
                List.of("zeta@x.jp", "alpha@x.jp", "mid@x.jp", "ALPHA@x.jp"));
        assertEquals(List.of("zeta@x.jp", "alpha@x.jp", "mid@x.jp"), r,
                "申込順(最初に出てきた順)になっていません。HashSet は順不同、TreeSet は辞書順です。実際の結果: " + r);
    }

    @Test
    @DisplayName("null と空白だけの要素は捨てる")
    void skipNullAndBlank() {
        List<String> input = new ArrayList<>(Arrays.asList("", null, "  ", "a@x.jp", null));
        List<String> r = assertDoesNotThrow(() -> MailingList.normalize(input),
                "null の要素で例外が出ています。e == null || e.isBlank() なら continue してください");
        assertEquals(List.of("a@x.jp"), r, "null・空白だけの要素は結果に入れないでください。実際の結果: " + r);
        assertTrue(MailingList.normalize(Arrays.asList("", null, " ")).isEmpty(),
                "有効なアドレスが1つもなければ空のリストを返してください");
    }

    @Test
    @DisplayName("引数のリストは変更しない(不変リストでも例外にならない)")
    void doesNotModifyInput() {
        List<String> input = new ArrayList<>(List.of("A@x.jp", "a@x.jp"));
        MailingList.normalize(input);
        assertEquals(List.of("A@x.jp", "a@x.jp"), input,
                "引数のリストが書き換えられています。新しい Set / List を作って、そちらに詰めてください");
        assertDoesNotThrow(() -> MailingList.normalize(List.of("A@x.jp", "a@x.jp")),
                "不変リスト(List.of)を渡すと例外が出ています。引数のリストに remove や set をしていませんか?");
    }

    @Test
    @DisplayName("emails が null なら空のリスト")
    void nullInput() {
        List<String> r = assertDoesNotThrow(() -> MailingList.normalize(null),
                "emails が null のときに例外が出ています。最初に null チェックをしてください");
        assertNotNull(r, "null ではなく空のリストを返してください");
        assertTrue(r.isEmpty(), "emails が null なら空のリストを返してください");
    }
}
