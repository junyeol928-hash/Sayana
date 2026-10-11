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
        if (actor == null) {
            return Decision.deny(LOGIN_REQUIRED);
        }
        boolean owner = actor.employeeId() == req.applicantId();
        boolean approver = actor.role() == Role.APPROVER || actor.role() == Role.ADMIN;

        // 1. 閲覧:本人か承認者・管理者
        if (op == Operation.VIEW) {
            return owner || approver ? Decision.allow() : Decision.deny(NO_VIEW);
        }

        // 2. 誰が(ルール1・2)
        switch (op) {
            case SUBMIT, WITHDRAW -> {
                if (!owner) {
                    return Decision.deny(NOT_OWNER);
                }
            }
            case APPROVE, RETURN -> {
                if (!approver) {
                    return Decision.deny(NOT_APPROVER);
                }
                if (op == Operation.APPROVE && owner) {
                    return Decision.deny(OWN_REQUEST);
                }
            }
            case VIEW -> throw new AssertionError("VIEW は上で処理済み");
        }

        // 3. どの状態で(ルール5と状態遷移図)
        RequestStatus s = req.status();
        if (s == RequestStatus.APPROVED || s == RequestStatus.WITHDRAWN) {
            return Decision.deny(FINAL_STATUS);
        }
        boolean statusOk = switch (op) {
            case SUBMIT, WITHDRAW -> s == RequestStatus.DRAFT || s == RequestStatus.RETURNED;
            case APPROVE, RETURN -> s == RequestStatus.SUBMITTED;
            case VIEW -> true;
        };
        if (!statusOk) {
            return Decision.deny(BAD_STATUS);
        }

        // 4. 高額の承認は管理者だけ(ルール3)。休暇は amount が null
        if (op == Operation.APPROVE && actor.role() != Role.ADMIN
                && req.amount() != null && req.amount() >= HIGH_AMOUNT_THRESHOLD) {
            return Decision.deny(HIGH_AMOUNT);
        }
        return Decision.allow();
    }
}
