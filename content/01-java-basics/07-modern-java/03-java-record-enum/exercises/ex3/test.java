import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationFlowTest {

    @Test
    @DisplayName("矢印がある遷移は true(提出・承認・却下・差し戻し・再申請の準備)")
    void allowed() {
        assertTrue(Status.DRAFT.canMoveTo(Status.SUBMITTED), "DRAFT → SUBMITTED(提出)は遷移できます");
        assertTrue(Status.SUBMITTED.canMoveTo(Status.APPROVED), "SUBMITTED → APPROVED(承認)は遷移できます");
        assertTrue(Status.SUBMITTED.canMoveTo(Status.REJECTED), "SUBMITTED → REJECTED(却下)は遷移できます");
        assertTrue(Status.SUBMITTED.canMoveTo(Status.DRAFT), "SUBMITTED → DRAFT(差し戻し)は遷移できます");
        assertTrue(Status.REJECTED.canMoveTo(Status.DRAFT), "REJECTED → DRAFT(再申請の準備)は遷移できます");
    }

    @Test
    @DisplayName("段階を飛ばす遷移や逆戻りは false")
    void skipped() {
        assertFalse(Status.DRAFT.canMoveTo(Status.APPROVED), "DRAFT から申請を飛ばして APPROVED にはできません");
        assertFalse(Status.DRAFT.canMoveTo(Status.REJECTED), "DRAFT から REJECTED にはできません");
        assertFalse(Status.REJECTED.canMoveTo(Status.SUBMITTED), "REJECTED から直接 SUBMITTED にはできません(一度 DRAFT に戻します)");
    }

    @Test
    @DisplayName("APPROVED は最終状態でどこにも遷移できない。同じ状態への遷移と null も false")
    void finalAndSame() {
        for (Status s : Status.values()) {
            assertFalse(Status.APPROVED.canMoveTo(s), "APPROVED(最終状態)から " + s + " へは遷移できません");
            assertFalse(s.canMoveTo(s), s + " から同じ " + s + " への遷移は false にしてください");
        }
        assertFalse(Status.SUBMITTED.canMoveTo(null), "next が null なら(例外ではなく)false を返してください");
    }

    @Test
    @DisplayName("countByStatus:件数を数え、0件のステータスも 0 で入れる")
    void count() {
        Map<Status, Integer> counts = ApplicationFlow.countByStatus(
                List.of(Status.DRAFT, Status.SUBMITTED, Status.DRAFT));
        assertEquals(2, counts.get(Status.DRAFT), "DRAFT は 2 件です");
        assertEquals(1, counts.get(Status.SUBMITTED), "SUBMITTED は 1 件です");
        assertEquals(0, counts.get(Status.APPROVED),
                "APPROVED は 0 件です。0 件のステータスもキーとして 0 を入れておいてください");
        assertEquals(0, counts.get(Status.REJECTED), "REJECTED は 0 件です");
    }

    @Test
    @DisplayName("countByStatus:空のリストなら全ステータス 0 件、順番は宣言順")
    void countEmpty() {
        Map<Status, Integer> counts = ApplicationFlow.countByStatus(List.of());
        assertEquals("{DRAFT=0, SUBMITTED=0, APPROVED=0, REJECTED=0}", counts.toString(),
                "空のリストでも全ステータスを 0 件で返してください(EnumMap なら宣言順に並びます)");
    }
}
