import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApprovalServiceTest {

    // 社員:1=申請者 花子、2=承認者 太郎、3=管理者 部長、4=申請者 次郎
    private static final Employee HANAKO = new Employee(1L, "申請 花子", Role.APPLICANT);
    private static final Employee TARO = new Employee(2L, "承認 太郎", Role.APPROVER);
    private static final Employee BUCHO = new Employee(3L, "管理 部長", Role.ADMIN);
    private static final Employee JIRO = new Employee(4L, "申請 次郎", Role.APPLICANT);

    private InMemoryApprovalRequestRepository requests;
    private InMemoryApprovalHistoryRepository histories;
    private ApprovalService service;
    private long nextId = 100;

    @BeforeEach
    void setUp() {
        requests = new InMemoryApprovalRequestRepository();
        histories = new InMemoryApprovalHistoryRepository();
        InMemoryEmployeeRepository employees = new InMemoryEmployeeRepository();
        List.of(HANAKO, TARO, BUCHO, JIRO).forEach(employees::add);
        service = new ApprovalService(requests, employees, histories);
    }

    /** 下書きを1件作る */
    private ApprovalRequest draft(Employee applicant, Integer amount) {
        return requests.save(new ApprovalRequest(nextId++, "テスト申請", amount, applicant));
    }

    /** 申請中の申請を1件作る(履歴は残さない) */
    private ApprovalRequest submitted(Employee applicant, Integer amount) {
        ApprovalRequest r = draft(applicant, amount);
        r.submit(java.time.LocalDateTime.of(2026, 4, 1, 9, 0));
        return r;
    }

    private List<Action> actions(ApprovalRequest r) {
        return histories.findByRequestId(r.getId()).stream().map(ApprovalHistory::action).toList();
    }

    private void assertRule(String expected, ApprovalRequest r, Runnable op, String hint) {
        RequestStatus before = r.getStatus();
        int historyCount = histories.findByRequestId(r.getId()).size();
        BusinessRuleException e = assertThrows(BusinessRuleException.class, op::run, hint);
        assertEquals(expected, e.getMessage(), "BusinessRuleException のメッセージが違います");
        assertEquals(before, r.getStatus(), "ルール違反のときは申請の状態を変えないでください(チェックはエンティティを呼ぶ前に)");
        assertEquals(historyCount, histories.findByRequestId(r.getId()).size(),
                "ルール違反のときは履歴を残さないでください(履歴の保存は、状態の変更が成功した後に)");
    }

    @Test
    @DisplayName("ルール1:申請・取下げは申請者本人だけ。成功したら SUBMIT の履歴が1件")
    void onlyApplicant() {
        ApprovalRequest r = draft(HANAKO, 1200);
        assertRule("申請者本人しか操作できません", r, () -> service.submit(r.getId(), JIRO.id()),
                "他人(次郎)が花子の申請を申請できてしまいました");
        assertRule("申請者本人しか操作できません", r, () -> service.withdraw(r.getId(), TARO.id()),
                "承認者でも、他人の申請は取り下げられないはずです");

        ApprovalRequest result = service.submit(r.getId(), HANAKO.id());
        assertNotNull(result, "submit は状態を変えた申請を返してください");
        assertEquals(RequestStatus.SUBMITTED, r.getStatus(), "本人が submit したら SUBMITTED になるはずです(r.submit を呼ぶ)");
        List<ApprovalHistory> h = histories.findByRequestId(r.getId());
        assertEquals(1, h.size(), "submit したら履歴を1件だけ保存してください(ルール6)");
        assertEquals(Action.SUBMIT, h.get(0).action(), "履歴の action は SUBMIT です");
        assertEquals(HANAKO.id(), h.get(0).actorId(), "履歴の actorId は操作した社員(花子)です");
        assertNotNull(h.get(0).at(), "履歴の at(日時)を入れてください");
    }

    @Test
    @DisplayName("ルール3の境界:99,999円は承認者OK、100,000円ちょうどは管理者だけ、休暇(null)は承認者OK")
    void adminLimit() {
        ApprovalRequest r99999 = submitted(HANAKO, 99_999);
        service.approve(r99999.getId(), TARO.id(), null);
        assertEquals(RequestStatus.APPROVED, r99999.getStatus(), "99,999円は承認者(APPROVER)が承認できるはずです");

        ApprovalRequest r100000 = submitted(HANAKO, 100_000);
        assertRule("100,000円以上の申請は管理者しか承認できません", r100000,
                () -> service.approve(r100000.getId(), TARO.id(), null),
                "100,000円ちょうどを承認者が承認できてしまいました。「以上」なので ApprovalFlow.requiresAdmin を使ってください");
        service.approve(r100000.getId(), BUCHO.id(), "了解");
        assertEquals(RequestStatus.APPROVED, r100000.getStatus(), "100,000円は管理者(ADMIN)なら承認できるはずです");
        assertEquals(List.of(Action.APPROVE), actions(r100000), "承認できたら APPROVE の履歴を1件だけ残してください");

        ApprovalRequest leave = submitted(HANAKO, null);
        service.approve(leave.getId(), TARO.id(), null);
        assertEquals(RequestStatus.APPROVED, leave.getStatus(), "休暇(金額 null)は承認者が承認できるはずです(NullPointerException にしない)");
    }

    @Test
    @DisplayName("ルール2:自分の申請は承認できない。申請者(APPLICANT)は承認・差戻しできない")
    void approverOnly() {
        ApprovalRequest own = submitted(TARO, 5_000);
        assertRule("自分の申請は承認できません", own, () -> service.approve(own.getId(), TARO.id(), null),
                "承認者が自分の申請を承認できてしまいました");

        ApprovalRequest r = submitted(HANAKO, 5_000);
        assertRule("承認者・管理者しか操作できません", r, () -> service.approve(r.getId(), JIRO.id(), null),
                "申請者(APPLICANT)の次郎が承認できてしまいました");
        assertRule("承認者・管理者しか操作できません", r, () -> service.returnBack(r.getId(), JIRO.id(), "だめです"),
                "申請者(APPLICANT)の次郎が差し戻せてしまいました");
    }

    @Test
    @DisplayName("ルール4:空白コメントの差戻しは失敗して履歴も残らない。コメントがあれば RETURN の履歴")
    void returnNeedsComment() {
        ApprovalRequest r = submitted(HANAKO, 3_000);
        assertRule("差戻しにはコメントが必要です", r, () -> service.returnBack(r.getId(), TARO.id(), "  "),
                "空白だけのコメントで差し戻せてしまいました(r.returnBack に任せれば例外になります)");

        service.returnBack(r.getId(), TARO.id(), "領収書を添付してください");
        assertEquals(RequestStatus.RETURNED, r.getStatus(), "コメント付きなら RETURNED になるはずです");
        List<ApprovalHistory> h = histories.findByRequestId(r.getId());
        assertEquals(1, h.size(), "差戻しの履歴を1件だけ残してください");
        assertEquals(Action.RETURN, h.get(0).action(), "差戻しの履歴の action は RETURN です");
        assertEquals("領収書を添付してください", h.get(0).comment(), "差戻しの履歴にコメントを残してください");
    }

    @Test
    @DisplayName("ルール5:承認済の申請は、承認し直しも取下げもできず、履歴も増えない")
    void approvedIsFinal() {
        ApprovalRequest r = draft(HANAKO, 8_000);
        service.submit(r.getId(), HANAKO.id());
        service.approve(r.getId(), TARO.id(), null);
        assertRule("承認済の申請は変更できません", r, () -> service.approve(r.getId(), BUCHO.id(), null),
                "承認済の申請をもう一度承認できてしまいました");
        assertRule("承認済の申請は変更できません", r, () -> service.withdraw(r.getId(), HANAKO.id()),
                "承認済の申請を取り下げられてしまいました");
        assertEquals(List.of(Action.SUBMIT, Action.APPROVE), actions(r), "履歴は SUBMIT と APPROVE の2件のままのはずです");
    }

    @Test
    @DisplayName("ルール6:申請 → 差戻し → 再申請 → 承認 で、操作のたびに履歴が1件ずつ残る")
    void historyPerOperation() {
        ApprovalRequest r = draft(HANAKO, 12_000);
        service.submit(r.getId(), HANAKO.id());
        service.returnBack(r.getId(), TARO.id(), "金額の根拠を書いてください");
        service.submit(r.getId(), HANAKO.id());
        service.approve(r.getId(), TARO.id(), "確認しました");

        List<ApprovalHistory> h = histories.findByRequestId(r.getId());
        assertEquals(List.of(Action.SUBMIT, Action.RETURN, Action.SUBMIT, Action.APPROVE),
                h.stream().map(ApprovalHistory::action).toList(),
                "操作のたびに、その操作の履歴を1件ずつ、操作した順に残してください");
        assertEquals(List.of(1L, 2L, 1L, 2L), h.stream().map(ApprovalHistory::actorId).toList(),
                "履歴の actorId は操作した社員の id です");
        assertEquals(RequestStatus.APPROVED, r.getStatus(), "最後は APPROVED のはずです");
    }
}
