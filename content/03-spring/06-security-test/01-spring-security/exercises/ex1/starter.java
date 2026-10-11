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
        // TODO: pattern が "/**" で終わるなら、末尾の "/**" を取り除いた部分を base として
        //       path が base と等しい、または base + "/" で始まるなら true
        //       ("/admin/**" は "/admin" と "/admin/users" に一致し、"/administrator" には一致しない)
        // TODO: それ以外は完全一致(equals)で判定する
        return false; // ← ダミー。書き換えてください
    }

    /** user が null なら未ログイン */
    public Decision check(String path, LoginUser user) {
        // TODO: rules を先頭から順に見て、matches で最初に一致したルールだけで判定する
        //   - PERMIT_ALL    → ALLOW(未ログインでも)
        //   - user が null  → LOGIN_REQUIRED
        //   - AUTHENTICATED → ALLOW
        //   - HAS_ROLE      → user.authorities() に "ROLE_" + role が含まれれば ALLOW、なければ FORBIDDEN
        // TODO: どのルールにも一致しなければ拒否
        //       (未ログインなら LOGIN_REQUIRED、ログイン済みなら FORBIDDEN)
        return null; // ← ダミー。書き換えてください
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
