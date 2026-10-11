import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagingParamsTest {

    private static String orders(PageRequest pr) {
        return pr.getSort().toString();
    }

    private static void assertSort(String expected, PageRequest pr, String input) {
        List<Sort.Order> list = pr.getSort().toList();
        assertEquals(expected, orders(pr),
                "sort=" + input + " のときの並び順が違います(1番目が指定の項目、2番目は常に id: DESC)");
        assertEquals(2, list.size(), "並び順のキーは「項目」と「id の降順」の2つにしてください");
    }

    @Test
    @DisplayName("何も指定しないと 0 ページ目・20 件・新しい順(createdAt 降順 → id 降順)")
    void defaults() {
        PageRequest pr = PagingParams.of(null, null, null);
        assertEquals(0, pr.getPageNumber(), "page 未指定は 0(0 始まりの先頭ページ)です");
        assertEquals(20, pr.getPageSize(), "size 未指定は既定の 20 件です");
        assertSort("createdAt: DESC,id: DESC", pr, "null");
        assertSort("createdAt: DESC,id: DESC", PagingParams.of("", " ", ""), "空文字");
    }

    @Test
    @DisplayName("page は数字なら使い、負・数字以外・桁あふれは 0 にする")
    void page() {
        assertEquals(2, PagingParams.of("2", null, null).getPageNumber(), "page=\"2\" は 2 ページ目(0 始まり)です");
        assertEquals(3, PagingParams.of(" 3 ", null, null).getPageNumber(), "前後の空白は取り除いてから数字にします");
        assertEquals(0, PagingParams.of("-1", null, null).getPageNumber(), "負の page は 0 にします(PageRequest.of は負で例外になります)");
        assertEquals(0, PagingParams.of("abc", null, null).getPageNumber(), "数字でない page は 0 にします");
        assertEquals(0, PagingParams.of("99999999999", null, null).getPageNumber(),
                "int に収まらない page は NumberFormatException になるので 0 にします");
    }

    @Test
    @DisplayName("size は 1〜50 に丸め、数字でなければ 20 にする")
    void size() {
        assertEquals(1, PagingParams.of(null, "0", null).getPageSize(), "size=0 は 1 に丸めます(PageRequest.of は 0 で例外になります)");
        assertEquals(1, PagingParams.of(null, "-5", null).getPageSize(), "負の size は 1 に丸めます");
        assertEquals(50, PagingParams.of(null, "50", null).getPageSize(), "size=50 はちょうど上限なのでそのままです");
        assertEquals(50, PagingParams.of(null, "100000", null).getPageSize(), "上限 50 を超える size は 50 に丸めます");
        assertEquals(20, PagingParams.of(null, "x", null).getPageSize(), "数字でない size は既定の 20 にします");
    }

    @Test
    @DisplayName("許可した項目は指定の方向で並べ、方向の省略は昇順")
    void allowedSort() {
        assertSort("amount: ASC,id: DESC", PagingParams.of(null, null, "amount,asc"), "amount,asc");
        assertSort("submittedAt: DESC,id: DESC", PagingParams.of(null, null, "submittedAt,DESC"), "submittedAt,DESC");
        assertSort("amount: ASC,id: DESC", PagingParams.of(null, null, "amount"), "amount(方向の省略は昇順)");
        assertSort("createdAt: ASC,id: DESC", PagingParams.of(null, null, " createdAt , asc "), "前後に空白つき");
    }

    @Test
    @DisplayName("許可していない項目・不正な方向・おかしな形は createdAt の降順に戻す")
    void invalidSort() {
        for (String s : new String[] {"password,asc", "applicant.email,desc", "title",
                "amount,up", "amount,asc,id", ",", "amount,"}) {
            assertSort("createdAt: DESC,id: DESC", PagingParams.of(null, null, s), "\"" + s + "\"");
        }
    }
}
