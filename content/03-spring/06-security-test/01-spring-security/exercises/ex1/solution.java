import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class UrlAuthorizer {

    private final List<Rule> rules = new ArrayList<>();

    // ---- ルールの登録(Spring Security の authorizeHttpRequests の縮小版。ここは完成済み) ----

    public UrlAuthorizer permitAll(String... patterns) {
        for (String p : patterns) {
            rules.add(new Rule(p, Access.PERMIT_ALL, null));
        }
        return this;
    }

    public UrlAuthorizer authenticated(String... patterns) {
        for (String p : patterns) {
            rules.add(new Rule(p, Access.AUTHENTICATED, null));
        }
        return this;
    }

    public UrlAuthorizer hasRole(String pattern, String role) {
        rules.add(new Rule(pattern, Access.HAS_ROLE, role));
        return this;
    }

    // ---- ここから下を実装する ----

    /** pattern が path に一致するか。"/xxx/**" は "/xxx" とその配下すべて、それ以外は完全一致 */
    static boolean matches(String pattern, String path) {
        if (pattern.endsWith("/**")) {
            String base = pattern.substring(0, pattern.length() - 3);
            return path.equals(base) || path.startsWith(base + "/");
        }
        return pattern.equals(path);
    }

    /** user が null なら未ログイン */
    public Decision check(String path, LoginUser user) {
        for (Rule rule : rules) {
            if (!matches(rule.pattern(), path)) {
                continue;
            }
            // 最初に一致したルールだけで決める
            return decide(rule, user);
        }
        // どのルールにも一致しない URL は拒否(Spring Security 6 と同じ)
        return user == null ? Decision.LOGIN_REQUIRED : Decision.FORBIDDEN;
    }

    private static Decision decide(Rule rule, LoginUser user) {
        if (rule.access() == Access.PERMIT_ALL) {
            return Decision.ALLOW;
        }
        if (user == null) {
            return Decision.LOGIN_REQUIRED; // 認証がまだ
        }
        if (rule.access() == Access.AUTHENTICATED) {
            return Decision.ALLOW;
        }
        // HAS_ROLE: hasRole("ADMIN") は権限 "ROLE_ADMIN" を持っているかで判定する
        return user.authorities().contains("ROLE_" + rule.role())
                ? Decision.ALLOW
                : Decision.FORBIDDEN; // 認証は済んだが認可で拒否
    }

    public static void main(String[] args) {
        UrlAuthorizer auth = new UrlAuthorizer()
                .permitAll("/", "/login", "/css/**")
                .hasRole("/admin/**", "ADMIN")
                .authenticated("/**");
        LoginUser haru = new LoginUser("haru", Set.of("ROLE_USER"));
        LoginUser mocha = new LoginUser("mocha", Set.of("ROLE_USER", "ROLE_ADMIN"));
        System.out.println(auth.check("/css/app.css", null)); // ALLOW
        System.out.println(auth.check("/orders", null));      // LOGIN_REQUIRED
        System.out.println(auth.check("/admin/users", haru)); // FORBIDDEN
        System.out.println(auth.check("/admin/users", mocha)); // ALLOW
    }
}

/** 判定結果 */
enum Decision { ALLOW, LOGIN_REQUIRED, FORBIDDEN }

/** ルールの種類 */
enum Access { PERMIT_ALL, AUTHENTICATED, HAS_ROLE }

/** URL パターンと必要な権限の組。role は HAS_ROLE のときだけ使う */
record Rule(String pattern, Access access, String role) { }

/** ログイン中のユーザー。authorities は "ROLE_USER" のように ROLE_ 付きで持つ */
record LoginUser(String name, Set<String> authorities) { }
