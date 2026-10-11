import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmployeeTest {

    @Test
    @DisplayName("正社員:基本給 + 残業時間 × 2500")
    void fullTime() {
        assertEquals(325000, new FullTimeEmployee("田中", 300000, 10).monthlyPay(),
                "基本給 300000・残業 10 時間なら 300000 + 10 × 2500 = 325000 です");
        assertEquals(300000, new FullTimeEmployee("田中", 300000, 0).monthlyPay(),
                "残業 0 時間なら基本給そのままです");
    }

    @Test
    @DisplayName("パート:160 時間以内は 時給 × 時間")
    void partTimeRegular() {
        assertEquals(120000, new PartTimeEmployee("鈴木", 1200, 100).monthlyPay(),
                "時給 1200・100 時間なら 120000 です");
        assertEquals(160000, new PartTimeEmployee("鈴木", 1000, 160).monthlyPay(),
                "ちょうど 160 時間は割増なしで 1000 × 160 = 160000 です(境界値。> と >= を確認してください)");
    }

    @Test
    @DisplayName("パート:160 時間を超えた分だけ 1.25 倍")
    void partTimeOvertime() {
        assertEquals(161250, new PartTimeEmployee("高橋", 1000, 161).monthlyPay(),
                "161 時間なら 160 × 1000 + 1 × 1250 = 161250 です。超えた 1 時間だけが 1.25 倍です");
        assertEquals(172500, new PartTimeEmployee("高橋", 1000, 170).monthlyPay(),
                "170 時間なら 160 × 1000 + 10 × 1250 = 172500 です");
    }

    @Test
    @DisplayName("マネージャー:残業代なし、正社員の月給 + 役職手当 50000")
    void manager() {
        assertEquals(450000, new Manager("佐藤", 400000).monthlyPay(),
                "基本給 400000 のマネージャーは 400000 + 50000 = 450000 です。super(name, baseSalary, 0) で基本給を親に渡していますか?");
    }

    @Test
    @DisplayName("親の payslip() から子の monthlyPay() が使われる")
    void payslip() {
        assertEquals("佐藤: 450000円", new Manager("佐藤", 400000).payslip(),
                "payslip() は親のメソッドのままで、子でオーバーライドした monthlyPay() が使われるはずです");
        assertEquals("鈴木: 120000円", new PartTimeEmployee("鈴木", 1200, 100).payslip(),
                "パートの payslip() は \"鈴木: 120000円\" です");
    }
}
