import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageViewTest {

    /** 「申請1」〜「申請n」の件名リスト */
    private static List<String> titles(int n) {
        List<String> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add("申請" + i);
        }
        return list;
    }

    @Test
    @DisplayName("45 件・20 件ずつの先頭ページ:1〜20 件目、次ページあり")
    void firstPage() {
        PageView<String> p = PageView.of(titles(45), 0, 20);
        assertEquals(20, p.content().size(), "先頭ページの中身は 20 件です");
        assertEquals("申請1", p.content().get(0), "先頭ページの最初は 1 件目です(page は 0 始まり)");
        assertEquals(3, p.totalPages(), "45 件を 20 件ずつに分けると 3 ページ(切り上げ)です");
        assertFalse(p.hasPrevious(), "先頭ページ(number=0)に前のページはありません");
        assertTrue(p.hasNext(), "3 ページ中の 1 ページ目なので次のページがあります");
        assertEquals("全 45 件中 1〜20 件目", p.label(), "表示文字列が違います");
    }

    @Test
    @DisplayName("最後のページは件数が少ない:41〜45 件目、次ページなし")
    void lastPage() {
        PageView<String> p = PageView.of(titles(45), 2, 20);
        assertEquals(List.of("申請41", "申請42", "申請43", "申請44", "申請45"), p.content(),
                "最後のページは残りの 5 件だけです(subList の終わりを all.size() までにする)");
        assertTrue(p.hasPrevious(), "3 ページ目には前のページがあります");
        assertFalse(p.hasNext(), "最後のページに次のページはありません");
        assertEquals("全 45 件中 41〜45 件目", p.label(), "Y はこのページの実際の件数から計算します(60 ではありません)");
    }

    @Test
    @DisplayName("ちょうど割り切れる件数(40 件・20 件ずつ)は 2 ページ")
    void exactPages() {
        PageView<String> p = PageView.of(titles(40), 1, 20);
        assertEquals(2, p.totalPages(), "40 件 ÷ 20 件はちょうど 2 ページです(余分な空ページを作らない)");
        assertFalse(p.hasNext(), "2 ページ目が最後なので次はありません");
        assertEquals("全 40 件中 21〜40 件目", p.label(), "表示文字列が違います");
    }

    @Test
    @DisplayName("0 件と、範囲外のページ番号")
    void emptyAndOutOfRange() {
        PageView<String> empty = PageView.of(List.of(), 0, 20);
        assertEquals(0, empty.totalPages(), "0 件なら全ページ数は 0 です");
        assertFalse(empty.hasNext(), "0 件なら次のページはありません");
        assertEquals("該当する申請はありません", empty.label(), "0 件のときの表示文字列が違います");

        PageView<String> over = PageView.of(titles(45), 5, 20);
        assertTrue(over.content().isEmpty(), "範囲外のページ番号では、例外にせず中身を空にします");
        assertTrue(over.hasPrevious(), "範囲外でも number > 0 なら前のページはあります");
        assertFalse(over.hasNext(), "範囲外のページに次はありません");
        assertEquals("全 45 件(このページに申請はありません)", over.label(), "範囲外のページの表示文字列が違います");
    }

    @Test
    @DisplayName("件数は3桁区切りで表示し、巨大な page でも桁あふれしない")
    void formatAndOverflow() {
        PageView<String> p = PageView.of(titles(1234), 50, 20);
        assertEquals("全 1,234 件中 1,001〜1,020 件目", p.label(), "件数は String.format(\"%,d\", n) で3桁区切りにします");
        PageView<String> huge = PageView.of(titles(10), 200_000_000, 20);
        assertTrue(huge.content().isEmpty(),
                "page × size が int の範囲を超えても空のページを返します(開始位置を long で計算してください)");
    }

    @Test
    @DisplayName("page が負、size が 0 以下なら IllegalArgumentException")
    void invalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> PageView.of(titles(5), -1, 20),
                "page が負なら IllegalArgumentException を投げてください");
        assertThrows(IllegalArgumentException.class, () -> PageView.of(titles(5), 0, 0),
                "size が 0 なら IllegalArgumentException を投げてください(0 で割ることになります)");
    }
}
