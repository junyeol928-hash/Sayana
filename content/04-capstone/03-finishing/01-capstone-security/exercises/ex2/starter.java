import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

enum Role { APPLICANT, APPROVER, ADMIN }

/** 判定結果 */
enum Result { ALLOW, LOGIN_REQUIRED, FORBIDDEN }

/** 誰を通すか */
enum Kind { PERMIT_ALL, AUTHENTICATED, HAS_ANY_ROLE }

/** ルール1行分。roles は HAS_ANY_ROLE のときだけ使う */
record UrlRule(String pattern, Kind kind, Set<Role> roles) {}

public class UrlAccessTable {

    private final List<UrlRule> rules = new ArrayList<>();

    public UrlAccessTable permitAll(String... patterns) {
        for (String p : patterns) {
            rules.add(new UrlRule(p, Kind.PERMIT_ALL, Set.of()));
        }
        return this;
    }

    public UrlAccessTable authenticated(String... patterns) {
        for (String p : patterns) {
            rules.add(new UrlRule(p, Kind.AUTHENTICATED, Set.of()));
        }
        return this;
    }

    public UrlAccessTable hasAnyRole(Set<Role> roles, String... patterns) {
        for (String p : patterns) {
            rules.add(new UrlRule(p, Kind.HAS_ANY_ROLE, Set.copyOf(roles)));
        }
        return this;
    }

    /** 承認くんの SecurityFilterChain と同じ表(完成済み) */
    static UrlAccessTable approvalApp() {
        return new UrlAccessTable()
                .permitAll("/login", "/error", "/css/**", "/js/**")
                .hasAnyRole(Set.of(Role.APPROVER, Role.ADMIN),
                        "/approvals/**", "/requests/search",
                        "/requests/*/approve", "/requests/*/return", "/api/**")
                .authenticated("/**");
    }

    /** "/requests/5" → ["requests", "5"](空のセグメントは捨てる) */
    static String[] segments(String s) {
        return Arrays.stream(s.split("/")).filter(x -> !x.isEmpty()).toArray(String[]::new);
    }

    static boolean matches(String pattern, String path) {
        // TODO: segments() で区切り、1つずつ比べる
        //       "*" はちょうど1セグメント、末尾の "**" は残り0個以上に一致
        return false;
    }

    public Result decide(String path, Role role) {
        // TODO: 登録順に rules を見て、最初に matches したルールで決める
        //       PERMIT_ALL → ALLOW / 未ログイン → LOGIN_REQUIRED / AUTHENTICATED → ALLOW
        //       HAS_ANY_ROLE → roles に role があれば ALLOW、なければ FORBIDDEN
        // TODO: どのルールにも一致しなければ、未ログインは LOGIN_REQUIRED、ログイン済みは FORBIDDEN
        return Result.ALLOW;
    }
}
