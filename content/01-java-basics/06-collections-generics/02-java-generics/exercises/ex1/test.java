import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GenericToolsTest {

    @Test
    @DisplayName("Pair:getKey / getValue で入れた値を取り出せる")
    void pairGetters() {
        Pair<String, Integer> p = new Pair<>("りんご", 120);
        assertEquals("りんご", p.getKey(), "getKey() は key フィールドを返してください");
        assertEquals(120, p.getValue(), "getValue() は value フィールドを返してください");
    }

    @Test
    @DisplayName("Pair:swap はキーと値を入れ替えた新しい Pair を返し、元は変えない")
    void pairSwap() {
        Pair<String, Integer> p = new Pair<>("みかん", 80);
        Pair<Integer, String> s = p.swap();
        assertNotNull(s, "swap() が null を返しています。new Pair<>(value, key) を返してください");
        assertEquals(80, s.getKey(), "swap() 後のキーは元の値(80)になるはずです");
        assertEquals("みかん", s.getValue(), "swap() 後の値は元のキー(みかん)になるはずです");
        assertNotSame(p, s, "swap() は自分自身ではなく新しい Pair を返してください");
        assertEquals("みかん", p.getKey(), "swap() で元の Pair のキーが変わってしまっています");
    }

    @Test
    @DisplayName("max:Integer のリストで最大値を返す(先頭・末尾・負の数)")
    void maxInteger() {
        assertEquals(9, GenericTools.max(List.of(3, 9, 4)), "3, 9, 4 の最大は 9 です");
        assertEquals(9, GenericTools.max(List.of(9, 3, 4)), "最大値が先頭にあるときも 9 を返してください");
        assertEquals(9, GenericTools.max(List.of(3, 4, 9)), "最大値が末尾にあるときも 9 を返してください。ループが最後まで回っていますか?");
        assertEquals(-2, GenericTools.max(List.of(-5, -2, -9)),
                "すべて負の数なら -2 が最大です。最大値の初期値を 0 にしていませんか? 先頭の要素から始めてください");
    }

    @Test
    @DisplayName("max:String のリストで辞書順の最大を返す・要素1個")
    void maxString() {
        assertEquals("melon", GenericTools.max(List.of("kiwi", "apple", "melon")),
                "String は compareTo で辞書順に比べます。kiwi・apple・melon の最大は melon です");
        assertEquals("only", GenericTools.max(List.of("only")), "要素が1個ならその要素を返してください");
    }

    @Test
    @DisplayName("max:null や空のリストなら null を返す")
    void maxEmpty() {
        assertNull(assertDoesNotThrow(() -> GenericTools.max(new ArrayList<Integer>()),
                "空のリストで例外が出ています。list.isEmpty() なら null を返してください"),
                "空のリストなら null を返してください");
        assertNull(assertDoesNotThrow(() -> GenericTools.max((List<Integer>) null),
                "list が null のときに例外が出ています。最初に null チェックをしてください"),
                "list が null なら null を返してください");
    }
}
