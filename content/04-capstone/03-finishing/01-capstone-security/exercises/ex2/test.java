import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlAccessTableTest {

    private final UrlAccessTable table = UrlAccessTable.approvalApp();

    private void assertResult(Result expected, String path, Role role) {
        String who = role == null ? "未ログイン" : role.name();
        assertEquals(expected, table.decide(path, role), who + " が " + path + " にアクセスしたときの結果が違います");
    }

    @Test
    @DisplayName("matches:普通の文字列・* ・末尾の ** の一致")
    void matches() {
        assertTrue(UrlAccessTable.matches("/login", "/login"), "/login は /login に一致します");
        assertFalse(UrlAccessTable.matches("/login", "/login/x"), "/login は /login/x には一致しません(** がないので完全一致)");
        assertTrue(UrlAccessTable.matches("/requests/*/approve", "/requests/5/approve"), "* は 1 つのセグメント(5)に一致します");
        assertFalse(UrlAccessTable.matches("/requests/*/approve", "/requests/approve"), "* は 0 個のセグメントには一致しません");
        assertFalse(UrlAccessTable.matches("/requests/*/approve", "/requests/5/6/approve"), "* は 2 つのセグメントには一致しません");
        assertTrue(UrlAccessTable.matches("/approvals/**", "/approvals"), "末尾の ** は 0 個のセグメントにも一致します");
        assertTrue(UrlAccessTable.matches("/approvals/**", "/approvals/3/detail"), "末尾の ** は 2 つ以上のセグメントにも一致します");
        assertFalse(UrlAccessTable.matches("/approvals/**", "/approvalsX"), "/approvals/** は /approvalsX には一致しません(セグメント単位で比べる)");
        assertTrue(UrlAccessTable.matches("/**", "/"), "/** はトップページ / にも一致します");
    }

    @Test
    @DisplayName("未ログイン:ログイン画面と静的ファイルだけ通し、それ以外はログインへ")
    void anonymous() {
        assertResult(Result.ALLOW, "/login", null);
        assertResult(Result.ALLOW, "/css/app.css", null);
        assertResult(Result.LOGIN_REQUIRED, "/requests", null);
        assertResult(Result.LOGIN_REQUIRED, "/approvals", null);
    }

    @Test
    @DisplayName("申請者:自分の申請画面は通し、承認系・検索・API は 403")
    void applicant() {
        assertResult(Result.ALLOW, "/requests", Role.APPLICANT);
        assertResult(Result.ALLOW, "/requests/5", Role.APPLICANT);
        assertResult(Result.ALLOW, "/requests/5/submit", Role.APPLICANT);
        assertResult(Result.FORBIDDEN, "/approvals", Role.APPLICANT);
        assertResult(Result.FORBIDDEN, "/requests/search", Role.APPLICANT);
        assertResult(Result.FORBIDDEN, "/requests/5/approve", Role.APPLICANT);
        assertResult(Result.FORBIDDEN, "/api/requests/5", Role.APPLICANT);
    }

    @Test
    @DisplayName("承認者・管理者:承認系・検索・API を通す")
    void approverAndAdmin() {
        for (Role r : new Role[] {Role.APPROVER, Role.ADMIN}) {
            assertResult(Result.ALLOW, "/approvals", r);
            assertResult(Result.ALLOW, "/requests/search", r);
            assertResult(Result.ALLOW, "/requests/5/return", r);
            assertResult(Result.ALLOW, "/api/requests", r);
            assertResult(Result.ALLOW, "/requests/new", r);
        }
    }

    @Test
    @DisplayName("最初に一致したルールで決まる(広いルールを先に書くと穴があく)")
    void firstMatchWins() {
        UrlAccessTable wrongOrder = new UrlAccessTable()
                .authenticated("/requests/**")
                .hasAnyRole(Set.of(Role.APPROVER, Role.ADMIN), "/requests/search");
        assertEquals(Result.ALLOW, wrongOrder.decide("/requests/search", Role.APPLICANT),
                "先に一致した authenticated(\"/requests/**\") で決まるので、申請者でも通ってしまうのが正しい動きです"
                        + "(2つ目以降のルールを見てはいけません)");
    }

    @Test
    @DisplayName("どのルールにも一致しないとき:未ログインはログインへ、ログイン済みは 403")
    void noRuleMatched() {
        UrlAccessTable noDefault = new UrlAccessTable().permitAll("/login");
        assertEquals(Result.LOGIN_REQUIRED, noDefault.decide("/requests", null),
                "一致するルールがなく未ログインなら LOGIN_REQUIRED です");
        assertEquals(Result.FORBIDDEN, noDefault.decide("/requests", Role.ADMIN),
                "一致するルールがなくログイン済みなら FORBIDDEN です(管理者でも、許可していない URL は通さない)");
    }
}
