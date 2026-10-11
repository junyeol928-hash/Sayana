import java.util.Objects;

enum Role { APPLICANT, APPROVER, ADMIN }

enum RequestStatus { DRAFT, SUBMITTED, APPROVED, RETURNED, WITHDRAWN }

/** 画面やサービスで行う操作 */
enum Operation { VIEW, SUBMIT, WITHDRAW, APPROVE, RETURN }

/** ログイン中の社員(LoginUser の縮小版) */
record Actor(long employeeId, Role role) {}

/** 判定に必要な申請の情報(ApprovalRequest の縮小版)。amount は休暇なら null */
record RequestInfo(long applicantId, RequestStatus status, Integer amount) {}

/** 判定結果。許可なら reason は空文字 */
record Decision(boolean allowed, String reason) {
    static Decision allow() {
        return new Decision(true, "");
    }

    static Decision deny(String reason) {
        return new Decision(false, Objects.requireNonNull(reason));
    }
}

public class AccessPolicy {

    static final String LOGIN_REQUIRED = "ログインしてください";
    static final String NO_VIEW = "この申請を閲覧する権限がありません";
    static final String NOT_OWNER = "申請者本人しか操作できません";
    static final String NOT_APPROVER = "承認者・管理者しか操作できません";
    static final String OWN_REQUEST = "自分の申請は承認できません";
    static final String FINAL_STATUS = "承認済・取下げ済の申請は変更できません";
    static final String BAD_STATUS = "今の状態ではこの操作はできません";
    static final String HIGH_AMOUNT = "100,000円以上の申請は管理者しか承認できません";

    static final int HIGH_AMOUNT_THRESHOLD = 100_000;

    public Decision check(Actor actor, Operation op, RequestInfo req) {
        // TODO: 0. 未ログイン(actor が null)→ LOGIN_REQUIRED
        // TODO: 1. VIEW は「本人」か「APPROVER / ADMIN」なら許可、それ以外は NO_VIEW
        // TODO: 2. SUBMIT・WITHDRAW は本人だけ(NOT_OWNER)
        //          APPROVE・RETURN は APPROVER / ADMIN だけ(NOT_APPROVER)、APPROVE は自分の申請不可(OWN_REQUEST)
        // TODO: 3. APPROVED・WITHDRAWN は FINAL_STATUS、遷移できない状態は BAD_STATUS
        // TODO: 4. APPROVE で 100,000 円以上なら ADMIN だけ(HIGH_AMOUNT)。amount が null なら対象外
        return Decision.allow();
    }
}
