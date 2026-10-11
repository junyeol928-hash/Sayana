import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApprovalRequestTest {

    private static final Employee HANAKO = new Employee(1L, "申請 花子", Role.APPLICANT);
    private static final Employee TARO = new Employee(2L, "承認 太郎", Role.APPROVER);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 4, 1, 9, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 4, 2, 10, 30);

    private static ApprovalRequest submitted() {
        ApprovalRequest r = new ApprovalRequest(10L, "交通費", 1200, HANAKO);
        r.submit(T1);
        return r;
    }

    @Test
    @DisplayName("submit:DRAFT → SUBMITTED、申請日時を記録。差戻し後は再申請できる")
    void submit() {
        ApprovalRequest r = new ApprovalRequest(10L, "交通費", 1200, HANAKO);
        r.submit(T1);
        assertEquals(RequestStatus.SUBMITTED, r.getStatus(), "下書きを submit したら SUBMITTED にしてください(ApprovalFlow.next を使う)");
        assertEquals(T1, r.getSubmittedAt(), "submit では submittedAt に now を入れてください");

        r.returnBack(TARO, "領収書を添付してください", T2);
        r.submit(T2);
        assertEquals(RequestStatus.SUBMITTED, r.getStatus(), "差し戻された申請は submit で再申請(SUBMITTED)できるはずです");
        assertEquals(T2, r.getSubmittedAt(), "再申請のときは submittedAt を新しい日時にしてください");
    }

    @Test
    @DisplayName("approve:SUBMITTED → APPROVED、承認者・コメント・判断日時を記録")
    void approve() {
        ApprovalRequest r = submitted();
        r.approve(TARO, "OKです", T2);
        assertEquals(RequestStatus.APPROVED, r.getStatus(), "申請中の申請を approve したら APPROVED にしてください");
        assertSame(TARO, r.getApprover(), "approve では approver に承認した社員を入れてください");
        assertEquals("OKです", r.getComment(), "approve では comment を記録してください");
        assertEquals(T2, r.getDecidedAt(), "approve では decidedAt に now を入れてください");
    }

    @Test
    @DisplayName("returnBack:空白・null のコメントは例外で、状態は申請中のまま。コメントがあれば RETURNED")
    void returnBackNeedsComment() {
        ApprovalRequest r = submitted();
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> r.returnBack(TARO, "   ", T2),
                "空白だけのコメントで差し戻せてしまいました。comment.isBlank() なら BusinessRuleException を投げてください");
        assertEquals("差戻しにはコメントが必要です", e.getMessage(), "例外のメッセージが違います");
        assertThrows(BusinessRuleException.class, () -> r.returnBack(TARO, null, T2),
                "コメントが null でも差し戻せてしまいました");
        assertEquals(RequestStatus.SUBMITTED, r.getStatus(), "差戻しに失敗したときは状態を変えないでください");
        assertNull(r.getApprover(), "差戻しに失敗したときは approver も変えないでください(チェックを先に済ませる)");

        r.returnBack(TARO, "領収書を添付してください", T2);
        assertEquals(RequestStatus.RETURNED, r.getStatus(), "コメント付きの差戻しは RETURNED にしてください");
        assertEquals("領収書を添付してください", r.getComment(), "差戻しのコメントを記録してください");
        assertSame(TARO, r.getApprover(), "差戻しでも approver に判断した社員を入れてください");
        assertEquals(T2, r.getDecidedAt(), "差戻しでも decidedAt に now を入れてください");
    }

    @Test
    @DisplayName("APPROVED と WITHDRAWN は以後変更できない(業務ルール5)")
    void finalStatus() {
        ApprovalRequest approved = submitted();
        approved.approve(TARO, null, T2);
        BusinessRuleException e = assertThrows(BusinessRuleException.class, approved::withdraw,
                "承認済の申請を取り下げられてしまいました。ApprovalFlow.next で状態を求めてください");
        assertEquals("承認済の申請は変更できません", e.getMessage(), "ApprovalFlow.next が投げた例外をそのまま外へ出してください");
        assertThrows(BusinessRuleException.class, () -> approved.approve(TARO, null, T2), "承認済の申請をもう一度承認できてしまいました");
        assertEquals(RequestStatus.APPROVED, approved.getStatus(), "承認済のまま変わらないはずです");

        ApprovalRequest withdrawn = new ApprovalRequest(11L, "モニター", 30000, HANAKO);
        withdrawn.withdraw();
        assertEquals(RequestStatus.WITHDRAWN, withdrawn.getStatus(), "下書きは withdraw で WITHDRAWN にできるはずです");
        assertThrows(BusinessRuleException.class, () -> withdrawn.submit(T1), "取下げ済の申請を申請できてしまいました");
        assertNull(withdrawn.getSubmittedAt(), "submit に失敗したときは submittedAt を変えないでください");
    }

    @Test
    @DisplayName("図に矢印の無い操作は例外:申請中の取下げ・下書きの承認")
    void invalidTransition() {
        ApprovalRequest r = submitted();
        BusinessRuleException e = assertThrows(BusinessRuleException.class, r::withdraw,
                "申請中の申請を取り下げられてしまいました(状態遷移図に SUBMITTED → WITHDRAWN の矢印はありません)");
        assertEquals("申請中の申請は「取下げ」できません", e.getMessage(), "ApprovalFlow.next のメッセージをそのまま使ってください");

        ApprovalRequest draft = new ApprovalRequest(12L, "有給", null, HANAKO);
        assertThrows(BusinessRuleException.class, () -> draft.approve(TARO, null, T2), "下書きを承認できてしまいました");
        assertEquals(RequestStatus.DRAFT, draft.getStatus(), "失敗したときは DRAFT のままのはずです");
        assertNull(draft.getDecidedAt(), "承認に失敗したときは decidedAt を変えないでください");
    }
}
