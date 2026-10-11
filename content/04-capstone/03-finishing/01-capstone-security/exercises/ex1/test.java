import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessPolicyTest {

    private final AccessPolicy policy = new AccessPolicy();

    // 登場人物:1 = ハル(申請者)、2 = モカ(承認者)、3 = 部長(管理者)、4 = 別の申請者
    private static final Actor HARU = new Actor(1, Role.APPLICANT);
    private static final Actor MOCHA = new Actor(2, Role.APPROVER);
    private static final Actor BOSS = new Actor(3, Role.ADMIN);
    private static final Actor OTHER = new Actor(4, Role.APPLICANT);

    private static RequestInfo harusRequest(RequestStatus status, Integer amount) {
        return new RequestInfo(1, status, amount);
    }

    private void assertAllowed(Actor a, Operation op, RequestInfo r, String why) {
        Decision d = policy.check(a, op, r);
        assertEquals(true, d.allowed(), why + " は許可されるはずです(返った理由: " + d.reason() + ")");
        assertEquals("", d.reason(), why + ":許可のときの reason は空文字にしてください(Decision.allow())");
    }

    private void assertDenied(String expectedReason, Actor a, Operation op, RequestInfo r, String why) {
        Decision d = policy.check(a, op, r);
        assertEquals(false, d.allowed(), why + " は拒否されるはずです");
        assertEquals(expectedReason, d.reason(), why + " の拒否理由が違います(判定の順番を確認してください)");
    }

    @Test
    @DisplayName("未ログインは、どの操作でも「ログインしてください」")
    void notLoggedIn() {
        RequestInfo r = harusRequest(RequestStatus.SUBMITTED, 5_000);
        for (Operation op : Operation.values()) {
            assertDenied(AccessPolicy.LOGIN_REQUIRED, null, op, r, "未ログインで " + op);
        }
    }

    @Test
    @DisplayName("閲覧:本人と承認者・管理者だけ。他の申請者は見られない")
    void view() {
        RequestInfo r = harusRequest(RequestStatus.APPROVED, 5_000);
        assertAllowed(HARU, Operation.VIEW, r, "本人が自分の申請を閲覧");
        assertAllowed(MOCHA, Operation.VIEW, r, "承認者が他人の申請を閲覧");
        assertAllowed(BOSS, Operation.VIEW, r, "管理者が他人の申請を閲覧");
        assertDenied(AccessPolicy.NO_VIEW, OTHER, Operation.VIEW, r, "別の申請者がハルの申請を閲覧");
    }

    @Test
    @DisplayName("申請・取下げは本人だけ。状態遷移図にない操作は拒否(ルール1・5)")
    void submitAndWithdraw() {
        assertAllowed(HARU, Operation.SUBMIT, harusRequest(RequestStatus.DRAFT, 5_000), "本人が下書きを申請");
        assertAllowed(HARU, Operation.SUBMIT, harusRequest(RequestStatus.RETURNED, 5_000), "本人が差戻しを再申請");
        assertAllowed(HARU, Operation.WITHDRAW, harusRequest(RequestStatus.RETURNED, 5_000), "本人が差戻しを取下げ");
        assertDenied(AccessPolicy.NOT_OWNER, BOSS, Operation.SUBMIT, harusRequest(RequestStatus.DRAFT, 5_000),
                "管理者でも他人の下書きを申請");
        assertDenied(AccessPolicy.BAD_STATUS, HARU, Operation.WITHDRAW, harusRequest(RequestStatus.SUBMITTED, 5_000),
                "申請中(SUBMITTED)の取下げ");
        assertDenied(AccessPolicy.FINAL_STATUS, HARU, Operation.SUBMIT, harusRequest(RequestStatus.WITHDRAWN, 5_000),
                "取下げ済の申請を申請");
    }

    @Test
    @DisplayName("承認・差戻し:承認者・管理者だけ、自分の申請は承認できない(ルール2・5)")
    void approveAndReturn() {
        RequestInfo submitted = harusRequest(RequestStatus.SUBMITTED, 50_000);
        assertAllowed(MOCHA, Operation.APPROVE, submitted, "承認者が他人の 50,000 円の申請を承認");
        assertAllowed(MOCHA, Operation.RETURN, submitted, "承認者が他人の申請を差戻し");
        assertDenied(AccessPolicy.NOT_APPROVER, OTHER, Operation.APPROVE, submitted, "申請者ロールの人が承認");
        assertDenied(AccessPolicy.OWN_REQUEST, MOCHA, Operation.APPROVE,
                new RequestInfo(2, RequestStatus.SUBMITTED, 3_000), "承認者が自分の申請を承認");
        assertDenied(AccessPolicy.BAD_STATUS, MOCHA, Operation.APPROVE, harusRequest(RequestStatus.DRAFT, 50_000),
                "下書き(DRAFT)を承認");
        assertDenied(AccessPolicy.FINAL_STATUS, BOSS, Operation.RETURN, harusRequest(RequestStatus.APPROVED, 50_000),
                "承認済の申請を差戻し");
    }

    @Test
    @DisplayName("100,000 円以上の承認は管理者だけ(ルール3)。境界値と休暇(金額 null)")
    void highAmount() {
        assertAllowed(MOCHA, Operation.APPROVE, harusRequest(RequestStatus.SUBMITTED, 99_999), "承認者が 99,999 円を承認");
        assertDenied(AccessPolicy.HIGH_AMOUNT, MOCHA, Operation.APPROVE, harusRequest(RequestStatus.SUBMITTED, 100_000),
                "承認者がちょうど 100,000 円を承認(「以上」なので管理者だけ)");
        assertAllowed(BOSS, Operation.APPROVE, harusRequest(RequestStatus.SUBMITTED, 100_000), "管理者が 100,000 円を承認");
        assertAllowed(MOCHA, Operation.APPROVE, harusRequest(RequestStatus.SUBMITTED, null), "承認者が休暇(金額 null)を承認");
        assertAllowed(MOCHA, Operation.RETURN, harusRequest(RequestStatus.SUBMITTED, 500_000),
                "承認者が 500,000 円の申請を差戻し(ルール3は承認だけ)");
    }

    @Test
    @DisplayName("理由は「誰が → 状態 → 金額」の順で最初に引っかかったもの")
    void order() {
        assertDenied(AccessPolicy.NOT_APPROVER, OTHER, Operation.APPROVE, harusRequest(RequestStatus.APPROVED, 5_000),
                "申請者ロールの人が承認済の申請を承認(状態より先に「誰が」で拒否)");
        assertDenied(AccessPolicy.OWN_REQUEST, BOSS, Operation.APPROVE,
                new RequestInfo(3, RequestStatus.SUBMITTED, 300_000), "管理者が自分の高額申請を承認");
        assertDenied(AccessPolicy.BAD_STATUS, MOCHA, Operation.APPROVE, harusRequest(RequestStatus.RETURNED, 300_000),
                "承認者が差戻し中の高額申請を承認(金額より先に状態で拒否)");
    }
}
