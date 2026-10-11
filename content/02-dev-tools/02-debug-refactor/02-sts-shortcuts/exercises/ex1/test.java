import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// メソッド名・定数名を「名前で探す」仕組み(リフレクション)で確かめるテスト。
// リネーム前の starter でもコンパイルできるように、直接 Overtime.totalOvertimeMinutes(...) とは書いていない。
class OvertimeTest {

    private static int total(int... workedMinutes) {
        try {
            Method m = Overtime.class.getDeclaredMethod("totalOvertimeMinutes", int[].class);
            m.setAccessible(true);
            return (int) m.invoke(null, (Object) workedMinutes);
        } catch (NoSuchMethodException e) {
            fail("メソッド totalOvertimeMinutes(int[]) が見つかりません。"
                    + "calc を Alt+Shift+R で totalOvertimeMinutes に名前変更してください");
            return -1;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("calc が totalOvertimeMinutes に名前変更されている(古い名前は残さない)")
    void renamed() throws Exception {
        Method m = null;
        try {
            m = Overtime.class.getDeclaredMethod("totalOvertimeMinutes", int[].class);
        } catch (NoSuchMethodException e) {
            fail("メソッド totalOvertimeMinutes(int[]) が見つかりません。名前のつづりも確認してください");
        }
        assertTrue(Modifier.isStatic(m.getModifiers()), "totalOvertimeMinutes は static のままにしてください");
        assertEquals(int.class, m.getReturnType(), "totalOvertimeMinutes の戻り値は int のままにしてください");
        assertThrows(NoSuchMethodException.class,
                () -> Overtime.class.getDeclaredMethod("calc", int[].class),
                "古いメソッド calc が残っています。コピーして新しく作るのではなく、名前変更(Alt+Shift+R)してください");
    }

    @Test
    @DisplayName("480 が定数 STANDARD_MINUTES に抽出されている")
    void constantExtracted() throws Exception {
        Field f;
        try {
            f = Overtime.class.getDeclaredField("STANDARD_MINUTES");
        } catch (NoSuchFieldException e) {
            fail("定数 STANDARD_MINUTES が見つかりません。480 を選択して "
                    + "Alt+Shift+T > Extract Constant... で抽出してください");
            return;
        }
        int mod = f.getModifiers();
        assertTrue(Modifier.isStatic(mod) && Modifier.isFinal(mod),
                "STANDARD_MINUTES は static final の定数にしてください");
        assertEquals(int.class, f.getType(), "STANDARD_MINUTES の型は int にしてください");
        f.setAccessible(true);
        assertEquals(480, f.getInt(null), "STANDARD_MINUTES の値は 480(8時間)です");
    }

    @Test
    @DisplayName("計算結果はリファクタリング前と同じ(480/540/600/300 分 → 残業 180 分)")
    void sameResult() {
        assertEquals(180, total(480, 540, 600, 300),
                "残業は 540→60分、600→120分 の合計 180分のはずです。リファクタリングで計算が変わっていませんか?");
    }

    @Test
    @DisplayName("480分ちょうどは残業 0、481分なら 1(境界値)")
    void boundary() {
        assertEquals(0, total(480), "480分ちょうどは残業ではありません");
        assertEquals(1, total(481), "481分なら残業は1分です");
    }

    @Test
    @DisplayName("勤務記録が空なら 0")
    void empty() {
        assertEquals(0, total(), "勤務記録が空の配列なら 0 を返してください");
    }
}
