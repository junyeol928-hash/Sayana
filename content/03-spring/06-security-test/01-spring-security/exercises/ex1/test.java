import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlAuthorizerTest {

    private static final LoginUser HARU = new LoginUser("haru", Set.of("ROLE_USER"));
    private static final LoginUser MOCHA = new LoginUser("mocha", Set.of("ROLE_USER", "ROLE_ADMIN"));

    private static UrlAuthorizer standard() {
        return new UrlAuthorizer()
                .permitAll("/", "/login", "/css/**")
                .hasRole("/admin/**", "ADMIN")
                .authenticated("/**");
    }

    @Test
    @DisplayName("パターンの一致:/** は自分自身と配下に一致し、名前が似ているだけの URL には一致しない")
    void matches() {
        assertTrue(UrlAuthorizer.matches("/admin/**", "/admin/users/3"),
                "\"/admin/**\" は配下の \"/admin/users/3\" に一致するはずです");
        assertTrue(UrlAuthorizer.matches("/admin/**", "/admin"),
                "\"/admin/**\" は \"/admin\" 自身にも一致するはずです");
        assertFalse(UrlAuthorizer.matches("/admin/**", "/administrator"),
                "\"/admin/**\" が \"/administrator\" に一致しています。startsWith(\"/admin\") ではなく \"/admin/\" で始まるかを見てください(管理画面の鍵がすり抜ける穴になります)");
        assertTrue(UrlAuthorizer.matches("/login", "/login"), "\"/login\" は \"/login\" に完全一致するはずです");
        assertFalse(UrlAuthorizer.matches("/", "/orders"),
                "\"/\" は完全一致なので \"/orders\" には一致しません");
        assertTrue(UrlAuthorizer.matches("/**", "/orders/1"), "\"/**\" はすべての URL に一致するはずです");
    }

    @Test
    @DisplayName("permitAll の URL は未ログインでも ALLOW")
    void permitAll() {
        UrlAuthorizer auth = standard();
        assertEquals(Decision.ALLOW, auth.check("/", null), "\"/\" は permitAll なので未ログインでも ALLOW です");
        assertEquals(Decision.ALLOW, auth.check("/css/app.css", null),
                "\"/css/**\" は permitAll なので未ログインでも ALLOW です");
    }

    @Test
    @DisplayName("未ログインでログイン必須の URL に来たら LOGIN_REQUIRED(認証がまだ)")
    void loginRequired() {
        UrlAuthorizer auth = standard();
        assertEquals(Decision.LOGIN_REQUIRED, auth.check("/orders", null),
                "未ログイン(user が null)で authenticated の URL に来たら LOGIN_REQUIRED です");
        assertEquals(Decision.LOGIN_REQUIRED, auth.check("/admin/users", null),
                "未ログインなら、管理画面でもまず LOGIN_REQUIRED(ログイン画面へ)です。FORBIDDEN ではありません");
        assertEquals(Decision.ALLOW, auth.check("/orders", HARU),
                "ログイン済みなら authenticated の URL は ALLOW です");
    }

    @Test
    @DisplayName("hasRole:ROLE_ADMIN を持つ人だけ ALLOW、持たない人は FORBIDDEN(認可で拒否)")
    void hasRole() {
        UrlAuthorizer auth = standard();
        assertEquals(Decision.FORBIDDEN, auth.check("/admin/users", HARU),
                "ROLE_USER しか持たない haru が /admin/users に来たら FORBIDDEN です");
        assertEquals(Decision.ALLOW, auth.check("/admin/users", MOCHA),
                "ROLE_ADMIN を持つ mocha は /admin/users に入れるはずです");
        LoginUser noPrefix = new LoginUser("x", Set.of("ADMIN"));
        assertEquals(Decision.FORBIDDEN, auth.check("/admin", noPrefix),
                "hasRole(\"ADMIN\") は権限 \"ROLE_ADMIN\" を探します。\"ROLE_\" を付けて比べていますか?");
    }

    @Test
    @DisplayName("最初に一致したルールで決まる(順番が大事)")
    void firstMatchWins() {
        UrlAuthorizer wrongOrder = new UrlAuthorizer()
                .authenticated("/**")
                .hasRole("/admin/**", "ADMIN");
        assertEquals(Decision.ALLOW, wrongOrder.check("/admin/users", HARU),
                "先に \"/**\" (authenticated) が一致するので、順番を間違えた設定では haru も入れてしまいます。最初に一致したルールだけで判定していますか?");
    }

    @Test
    @DisplayName("どのルールにも一致しない URL は拒否")
    void noMatch() {
        UrlAuthorizer auth = new UrlAuthorizer().permitAll("/").hasRole("/admin/**", "ADMIN");
        assertEquals(Decision.FORBIDDEN, auth.check("/orders", MOCHA),
                "どのルールにも一致しない URL は、ログイン済みなら FORBIDDEN です");
        assertEquals(Decision.LOGIN_REQUIRED, auth.check("/orders", null),
                "どのルールにも一致しない URL は、未ログインなら LOGIN_REQUIRED です");
    }
}
