import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApprovalFlowTest {

    @Test
    @DisplayName("申請・承認・差戻し・再申請・取下げで、図のとおりの状態になる")
    void normalFlow() {
        assertEquals(RequestStatus.SUBMITTED, ApprovalFlow.next(RequestStatus.DRAFT, Action.SUBMIT),
                "下書きを申請したら SUBMITTED になるはずです");
        assertEquals(RequestStatus.APPROVED, ApprovalFlow.next(RequestStatus.SUBMITTED, Action.APPROVE),
                "申請中を承認したら APPROVED になるはずです");
        assertEquals(RequestStatus.RETURNED, ApprovalFlow.next(RequestStatus.SUBMITTED, Action.RETURN),
                "申請中を差し戻したら RETURNED になるはずです");
        assertEquals(RequestStatus.SUBMITTED, ApprovalFlow.next(RequestStatus.RETURNED, Action.SUBMIT),
                "差戻しを再申請したら SUBMITTED に戻るはずです");
        assertEquals(RequestStatus.WITHDRAWN, ApprovalFlow.next(RequestStatus.DRAFT, Action.WITHDRAW),
                "下書きを取り下げたら WITHDRAWN になるはずです");
        assertEquals(RequestStatus.WITHDRAWN, ApprovalFlow.next(RequestStatus.RETURNED, Action.WITHDRAW),
                "差戻しを取り下げたら WITHDRAWN になるはずです");
    }

    @Test
    @DisplayName("図にない操作は BusinessRuleException になり、状態名と操作名がメッセージに入る")
    void invalidAction() {
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> ApprovalFlow.next(RequestStatus.SUBMITTED, Action.WITHDRAW),
                "申請中の取下げは図にないので BusinessRuleException を投げてください");
        assertEquals("申請中の申請は「取下げ」できません", e.getMessage(),
                "メッセージは「<状態の表示名>の申請は「<操作の表示名>」できません」の形にしてください");

        BusinessRuleException e2 = assertThrows(BusinessRuleException.class,
                () -> ApprovalFlow.next(RequestStatus.DRAFT, Action.APPROVE),
                "下書きはまだ申請されていないので、承認すると BusinessRuleException になるはずです");
        assertEquals("下書きの申請は「承認」できません", e2.getMessage(),
                "label() で日本語の表示名を使ってください(DRAFT ではなく 下書き)");
    }

    @Test
    @DisplayName("承認済・取下げの申請は、どの操作でも「変更できません」になる")
    void finalStatus() {
        for (Action a : Action.values()) {
            BusinessRuleException e = assertThrows(BusinessRuleException.class,
                    () -> ApprovalFlow.next(RequestStatus.APPROVED, a),
                    "承認済の申請に " + a + " したら BusinessRuleException になるはずです(業務ルール5)");
            assertEquals("承認済の申請は変更できません", e.getMessage(),
                    "確定状態のときは、先に isFinal() で調べて「承認済の申請は変更できません」にしてください");
        }
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> ApprovalFlow.next(RequestStatus.WITHDRAWN, Action.SUBMIT),
                "取り下げた申請は再申請できないはずです");
        assertEquals("取下げの申請は変更できません", e.getMessage(),
                "取下げも確定状態です。メッセージは「取下げの申請は変更できません」になるはずです");
    }

    @Test
    @DisplayName("10万円の境界:99,999円は不要、100,000円ちょうどから ADMIN が必要")
    void adminLimitBoundary() {
        assertFalse(ApprovalFlow.requiresAdmin(99_999),
                "99,999円は 100,000円未満なので APPROVER で承認できるはずです");
        assertTrue(ApprovalFlow.requiresAdmin(100_000),
                "100,000円ちょうどは「100,000円以上」に入るので ADMIN が必要です(> ではなく >= で比べる)");
        assertTrue(ApprovalFlow.requiresAdmin(250_000),
                "250,000円は ADMIN が必要なはずです");
        assertFalse(ApprovalFlow.requiresAdmin(0),
                "0円は ADMIN 不要のはずです");
    }

    @Test
    @DisplayName("休暇申請(金額 null)は ADMIN 不要で、例外にもならない")
    void leaveHasNoAmount() {
        assertFalse(ApprovalFlow.requiresAdmin(null),
                "金額が null(休暇申請)のときは false を返してください。null を int と比べると NullPointerException になります");
    }
}
