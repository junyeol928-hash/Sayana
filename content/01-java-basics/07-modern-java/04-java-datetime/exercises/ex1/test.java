import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractDatesTest {

    @Test
    @DisplayName("満了日は「months か月後の前日」(年度契約・1か月契約)")
    void endDate() {
        assertEquals(LocalDate.of(2025, 3, 31), ContractDates.endDate(LocalDate.of(2024, 4, 1), 12),
                "2024-04-01 開始の12か月契約は 2025-03-31 満了です。plusMonths の後に minusDays(1) していますか?");
        assertEquals(LocalDate.of(2024, 4, 14), ContractDates.endDate(LocalDate.of(2024, 3, 15), 1),
                "2024-03-15 開始の1か月契約は 2024-04-14 満了です");
    }

    @Test
    @DisplayName("満了日がうるう年の2月末になるケース")
    void endDateLeapYear() {
        assertEquals(LocalDate.of(2024, 2, 29), ContractDates.endDate(LocalDate.of(2023, 3, 1), 12),
                "2023-03-01 開始の12か月契約は、2024-03-01 の前日の 2024-02-29 満了です(うるう年)");
    }

    @Test
    @DisplayName("契約月数が 0 以下なら IllegalArgumentException")
    void invalidMonths() {
        assertThrows(IllegalArgumentException.class,
                () -> ContractDates.endDate(LocalDate.of(2024, 4, 1), 0),
                "months が 0 なら IllegalArgumentException を投げてください");
    }

    @Test
    @DisplayName("更新案内日は満了日の1か月前。前月に同じ日がなければ月末")
    void noticeDate() {
        assertEquals(LocalDate.of(2024, 9, 15), ContractDates.noticeDate(LocalDate.of(2024, 10, 15)),
                "2024-10-15 満了なら案内日は 2024-09-15 です");
        assertEquals(LocalDate.of(2025, 2, 28), ContractDates.noticeDate(LocalDate.of(2025, 3, 31)),
                "2025-03-31 満了なら案内日は 2025-02-28 です(2月31日は存在しないので月末に丸められます)");
        assertEquals(LocalDate.of(2024, 2, 29), ContractDates.noticeDate(LocalDate.of(2024, 3, 31)),
                "2024-03-31 満了なら案内日は 2024-02-29 です(うるう年)");
    }

    @Test
    @DisplayName("残り日数:年をまたぐ・当日は0・過ぎたらマイナス")
    void daysLeft() {
        assertEquals(11, ContractDates.daysLeft(LocalDate.of(2024, 12, 25), LocalDate.of(2025, 1, 5)),
                "2024-12-25 から 2025-01-05 までは 11 日です。Period.getDays() ではなく ChronoUnit.DAYS.between を使っていますか?");
        assertEquals(0, ContractDates.daysLeft(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5)),
                "満了日当日の残り日数は 0 です");
        assertEquals(-5, ContractDates.daysLeft(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 5)),
                "満了日を5日過ぎていたら -5 です(引数の順番に注意)");
    }

    @Test
    @DisplayName("1年以上先でも総日数を返す")
    void daysLeftLong() {
        assertEquals(366, ContractDates.daysLeft(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)),
                "2024-01-01 から 2025-01-01 までは 366 日です(2024年はうるう年)");
    }
}
