import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatusTableTest {

    @Test
    @DisplayName("状態遷移図にある6本の矢印は、すべて移れる")
    void arrowsInDiagram() {
        assertTrue(RequestStatus.DRAFT.canTransitionTo(RequestStatus.SUBMITTED),
                "下書き → 申請中(申請)は移れるはずです");
        assertTrue(RequestStatus.SUBMITTED.canTransitionTo(RequestStatus.APPROVED),
                "申請中 → 承認済(承認)は移れるはずです");
        assertTrue(RequestStatus.SUBMITTED.canTransitionTo(RequestStatus.RETURNED),
                "申請中 → 差戻し(差戻し)は移れるはずです");
        assertTrue(RequestStatus.RETURNED.canTransitionTo(RequestStatus.SUBMITTED),
                "差戻し → 申請中(再申請)は移れるはずです");
        assertTrue(RequestStatus.DRAFT.canTransitionTo(RequestStatus.WITHDRAWN),
                "下書き → 取下げ は移れるはずです");
        assertTrue(RequestStatus.RETURNED.canTransitionTo(RequestStatus.WITHDRAWN),
                "差戻し → 取下げ は移れるはずです");
    }

    @Test
    @DisplayName("図にない近道(申請中の取下げ・下書きからいきなり承認)はできない")
    void noShortcut() {
        assertFalse(RequestStatus.SUBMITTED.canTransitionTo(RequestStatus.WITHDRAWN),
                "申請中 → 取下げ の矢印は図にありません。申請中は承認者の手元にあるので取り下げられません");
        assertFalse(RequestStatus.DRAFT.canTransitionTo(RequestStatus.APPROVED),
                "下書き → 承認済 の矢印は図にありません。申請を飛ばして承認はできません");
        assertFalse(RequestStatus.RETURNED.canTransitionTo(RequestStatus.APPROVED),
                "差戻し → 承認済 の矢印は図にありません。再申請してからでないと承認できません");
    }

    @Test
    @DisplayName("承認済・取下げは確定状態で、どこにも移れない")
    void finalStates() {
        for (RequestStatus s : Set.of(RequestStatus.APPROVED, RequestStatus.WITHDRAWN)) {
            assertTrue(s.isFinal(), s + " は isFinal() が true のはずです(業務ルール5)");
            for (RequestStatus to : RequestStatus.values()) {
                assertFalse(s.canTransitionTo(to), s + " → " + to + " は移れないはずです(確定した申請は変更不可)");
            }
        }
        for (RequestStatus s : Set.of(RequestStatus.DRAFT, RequestStatus.SUBMITTED, RequestStatus.RETURNED)) {
            assertFalse(s.isFinal(), s + " はまだ変更できる状態なので isFinal() は false のはずです");
        }
    }

    @Test
    @DisplayName("同じ状態への遷移と null は false になる(例外にしない)")
    void selfAndNull() {
        for (RequestStatus s : RequestStatus.values()) {
            assertFalse(s.canTransitionTo(s), s + " → " + s + "(同じ状態)は矢印がないので false のはずです");
            assertFalse(s.canTransitionTo(null), s + ".canTransitionTo(null) は例外にせず false を返してください");
        }
    }

    @Test
    @DisplayName("25通りの組み合わせのうち、移れるのはちょうど6通り")
    void exactlySix() {
        int count = 0;
        for (RequestStatus from : RequestStatus.values()) {
            for (RequestStatus to : RequestStatus.values()) {
                if (from.canTransitionTo(to)) {
                    count++;
                }
            }
        }
        assertEquals(6, count, "true になる組み合わせは図の矢印と同じ6通りのはずです。余計な矢印を足していないか確認してください");
    }
}
