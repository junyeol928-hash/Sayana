import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PayrollTest {

    private Employee[] sample() {
        return new Employee[] {
            new Employee("佐藤", "営業", 300000),
            null,
            new Employee("鈴木", "開発", 350000),
            new Employee("高橋", "営業", 280000),
        };
    }

    @Test
    @DisplayName("total:null の空き枠を飛ばして全員の月給を合計する")
    void total() {
        assertEquals(930000, Payroll.total(sample()),
                "300000 + 350000 + 280000 = 930000 になるはずです。null の要素は飛ばしてください");
        assertEquals(0, Payroll.total(new Employee[0]), "空の配列なら合計は 0 のはずです");
        assertEquals(0, Payroll.total(new Employee[3]), "全部 null の配列なら合計は 0 のはずです(NullPointerException にしない)");
    }

    @Test
    @DisplayName("totalByDept:部署名を equals で比べて合計する")
    void totalByDept() {
        String dept = new String("営業"); // 画面入力のつもり(リテラルとは別のオブジェクト)
        assertEquals(580000, Payroll.totalByDept(sample(), dept),
                "営業部の合計は 300000 + 280000 = 580000 のはずです。部署名を == で比べていないか確認してください");
        assertEquals(350000, Payroll.totalByDept(sample(), "開発"), "開発部の合計は 350000 のはずです");
        assertEquals(0, Payroll.totalByDept(sample(), "総務"), "該当者がいない部署なら 0 のはずです");
    }

    @Test
    @DisplayName("topEarner:月給が一番高い社員のインスタンスを返す")
    void topEarner() {
        Employee[] staff = sample();
        assertSame(staff[2], Payroll.topEarner(staff), "一番月給が高いのは鈴木(350000円)のはずです");
    }

    @Test
    @DisplayName("topEarner:同額なら先に出てくる社員、1人もいなければ null")
    void topEarnerEdge() {
        Employee a = new Employee("A", "営業", 300000);
        Employee b = new Employee("B", "開発", 300000);
        assertSame(a, Payroll.topEarner(new Employee[] {null, a, b}),
                "同額のときは先に出てくる社員を返してください(> と >= を確認)");
        assertNull(Payroll.topEarner(new Employee[0]), "空の配列なら null を返してください");
        assertNull(Payroll.topEarner(new Employee[2]), "全部 null の配列なら null を返してください");
    }

    @Test
    @DisplayName("raiseAll:配列の中の社員そのものの月給が上がる")
    void raiseAll() {
        Employee sato = new Employee("佐藤", "営業", 300000);
        Employee suzuki = new Employee("鈴木", "開発", 350000);
        Employee[] staff = {sato, null, suzuki};
        Payroll.raiseAll(staff, 10);
        assertEquals(330000, sato.salary, "300000円の10%アップは 330000円のはずです");
        assertEquals(385000, suzuki.salary, "350000円の10%アップは 385000円のはずです");
        Payroll.raiseAll(staff, 0);
        assertEquals(330000, sato.salary, "0%アップなら月給は変わらないはずです");
    }
}
