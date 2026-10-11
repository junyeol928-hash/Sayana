import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// d.plusDays(1) の戻り値を捨てると無限ループになるので、5秒で打ち切る
@Timeout(5)
class BusinessDaysTest {

    // 2024年 GW の祝日:4/29(月 昭和の日)、5/3(金 憲法記念日)、5/6(月 振替休日)
    private static final Set<LocalDate> GW = Set.of(
            LocalDate.of(2024, 4, 29), LocalDate.of(2024, 5, 3), LocalDate.of(2024, 5, 6));

    @Test
    @DisplayName("isBusinessDay:平日は true、土日と祝日は false")
    void isBusinessDay() {
        assertTrue(BusinessDays.isBusinessDay(LocalDate.of(2024, 6, 3), Set.of()), "2024-06-03 は月曜なので営業日です");
        assertFalse(BusinessDays.isBusinessDay(LocalDate.of(2024, 6, 1), Set.of()), "2024-06-01 は土曜なので営業日ではありません");
        assertFalse(BusinessDays.isBusinessDay(LocalDate.of(2024, 6, 2), Set.of()), "2024-06-02 は日曜なので営業日ではありません");
        assertFalse(BusinessDays.isBusinessDay(LocalDate.of(2024, 4, 29), GW), "2024-04-29 は月曜ですが祝日なので営業日ではありません");
    }

    @Test
    @DisplayName("count:月曜〜日曜の1週間は5営業日。GW期間は3営業日")
    void countWeek() {
        assertEquals(5, BusinessDays.count(LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 9), Set.of()),
                "2024-06-03(月)〜06-09(日)の営業日は5日です。土日を除いていますか?");
        assertEquals(3, BusinessDays.count(LocalDate.of(2024, 4, 29), LocalDate.of(2024, 5, 6), GW),
                "2024-04-29〜05-06 の営業日は 4/30・5/1・5/2 の3日です。祝日を除いていますか?");
    }

    @Test
    @DisplayName("count:両端を含む。1日だけの期間、逆転した期間(境界値)")
    void countBoundary() {
        assertEquals(1, BusinessDays.count(LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 3), Set.of()),
                "from と to が同じ平日なら 1 です(両端を含みます)");
        assertEquals(0, BusinessDays.count(LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 1), Set.of()),
                "from と to が同じ土曜なら 0 です");
        assertEquals(0, BusinessDays.count(LocalDate.of(2024, 6, 9), LocalDate.of(2024, 6, 3), Set.of()),
                "from が to より後なら 0 を返してください");
    }

    @Test
    @DisplayName("addBusinessDays:週末・祝日・月末をまたぐ")
    void addBusinessDays() {
        assertEquals(LocalDate.of(2024, 6, 10), BusinessDays.addBusinessDays(LocalDate.of(2024, 6, 7), 1, Set.of()),
                "2024-06-07(金)の1営業日後は土日を飛ばして 06-10(月)です");
        assertEquals(LocalDate.of(2024, 5, 7), BusinessDays.addBusinessDays(LocalDate.of(2024, 5, 2), 1, GW),
                "2024-05-02(木)の1営業日後は、祝日と土日を飛ばして 05-07(火)です");
        assertEquals(LocalDate.of(2024, 7, 3), BusinessDays.addBusinessDays(LocalDate.of(2024, 6, 28), 3, Set.of()),
                "2024-06-28(金)の3営業日後は月をまたいで 07-03(水)です");
    }

    @Test
    @DisplayName("addBusinessDays:0営業日後は start のまま、マイナスは IllegalArgumentException")
    void addZeroAndNegative() {
        assertEquals(LocalDate.of(2024, 6, 1), BusinessDays.addBusinessDays(LocalDate.of(2024, 6, 1), 0, Set.of()),
                "n が 0 なら、start が土曜でもそのまま返してください");
        assertThrows(IllegalArgumentException.class,
                () -> BusinessDays.addBusinessDays(LocalDate.of(2024, 6, 3), -1, Set.of()),
                "n がマイナスなら IllegalArgumentException を投げてください");
    }
}
