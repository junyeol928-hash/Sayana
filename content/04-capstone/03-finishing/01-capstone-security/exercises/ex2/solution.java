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
        String[] p = segments(pattern);
        String[] u = segments(path);
        for (int i = 0; i < p.length; i++) {
            if (p[i].equals("**") && i == p.length - 1) {
                return true;                  // 残りは 0 個以上なんでもよい
            }
            if (i >= u.length) {
                return false;                 // URL のほうが短い
            }
            if (!p[i].equals("*") && !p[i].equals(u[i])) {
                return false;
            }
        }
        return p.length == u.length;          // URL のほうが長ければ不一致
    }

    public Result decide(String path, Role role) {
        for (UrlRule rule : rules) {
            if (!matches(rule.pattern(), path)) {
                continue;
            }
            // 最初に一致したルールだけで決める
            if (rule.kind() == Kind.PERMIT_ALL) {
                return Result.ALLOW;
            }
            if (role == null) {
                return Result.LOGIN_REQUIRED;
            }
            if (rule.kind() == Kind.AUTHENTICATED) {
                return Result.ALLOW;
            }
            return rule.roles().contains(role) ? Result.ALLOW : Result.FORBIDDEN;
        }
        return role == null ? Result.LOGIN_REQUIRED : Result.FORBIDDEN;
    }
}
