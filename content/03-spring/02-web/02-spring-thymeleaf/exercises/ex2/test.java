import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.StringTemplateResource;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

class OrderPageTest {

    /** テンプレート名 → 中身(文字列)で探すリゾルバ */
    static class MapResolver extends StringTemplateResolver {
        private final Map<String, String> templates;

        MapResolver(Map<String, String> templates) {
            this.templates = templates;
            setTemplateMode(TemplateMode.HTML);
        }

        @Override
        protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate,
                String template, Map<String, Object> attributes) {
            String content = templates.get(template);
            if (content == null) {
                throw new IllegalArgumentException("テンプレート \"" + template + "\" はありません(使えるのは layout と detail)");
            }
            return new StringTemplateResource(content);
        }
    }

    private static final Order ORDERED = new Order(1001, "山田 太郎",
            LocalDateTime.of(2026, 10, 11, 9, 5), 3480, "ORDERED");
    private static final Order SHIPPED = new Order(2002, "佐藤 花子",
            LocalDateTime.of(2026, 1, 2, 18, 30), 1234567, "SHIPPED");

    /** コンテキストパス /shop のアプリで、detail を描画する */
    private static String render(String layout, Order order, int page) {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new MapResolver(Map.of("layout", layout, "detail", OrderPage.DETAIL)));
        MockHttpServletRequest request = new MockHttpServletRequest(new MockServletContext(), "GET", "/shop/orders/1");
        request.setContextPath("/shop");
        JakartaServletWebApplication app = JakartaServletWebApplication.buildApplication(request.getServletContext());
        WebContext ctx = new WebContext(app.buildExchange(request, new MockHttpServletResponse()), Locale.JAPAN);
        ctx.setVariable("order", order);
        ctx.setVariable("page", page);
        return engine.process("detail", ctx);
    }

    private static String render(Order order, int page) {
        return render(OrderPage.LAYOUT, order, page);
    }

    /** <p class="xxx">内容</p> の内容(空白は1つにまとめる) */
    private static String text(String html, String cls) {
        Matcher m = Pattern.compile("<p class=\"" + cls + "\"[^>]*>(.*?)</p>", Pattern.DOTALL).matcher(html);
        return m.find() ? m.group(1).strip().replaceAll("\\s+", " ") : null;
    }

    /** <a class="xxx" ... href="…"> の href(無ければ null) */
    private static String href(String html, String cls) {
        Matcher m = Pattern.compile("<a[^>]*class=\"" + cls + "\"[^>]*>").matcher(html);
        if (!m.find()) {
            return null;
        }
        Matcher h = Pattern.compile("href=\"([^\"]*)\"").matcher(m.group());
        return h.find() ? h.group(1) : "";
    }

    @Test
    @DisplayName("共通ヘッダーが LAYOUT のフラグメントから入る(h1 は「注文詳細」、ロゴのリンクは /shop/)")
    void header() {
        String html = render(ORDERED, 1);
        assertTrue(html.contains("<h1>注文詳細</h1>"), "ヘッダーの h1 が「注文詳細」になっていません。\n描画結果:\n" + html);
        assertTrue(html.contains("href=\"/shop/\""),
                "ロゴのリンクが /shop/ になっていません。ヘッダーは th:replace で LAYOUT のフラグメントを使ってください");
        String replaced = render("<header th:fragment=\"header(title)\" class=\"test-layout\" th:text=\"${title}\"></header>",
                ORDERED, 1);
        assertTrue(replaced.contains("<header class=\"test-layout\">注文詳細</header>"),
                "ヘッダーを DETAIL に直接書いていませんか? th:replace=\"~{layout :: header('注文詳細')}\" でフラグメントを呼び出してください");
    }

    @Test
    @DisplayName("注文番号・お客様名が *{…} で表示される")
    void idAndCustomer() {
        String html = render(SHIPPED, 1);
        assertEquals("注文番号: 2002", text(html, "id"),
                "注文番号が違います。th:object=\"${order}\" の中で *{id} を使って出してください(ダミーのままになっていませんか?)");
        assertEquals("お客様: 佐藤 花子 様", text(html, "customer"), "「お客様: ○○ 様」の形で出してください");
    }

    @Test
    @DisplayName("注文日時は yyyy/MM/dd HH:mm、合計は3桁区切り+円")
    void dateAndTotal() {
        String html = render(ORDERED, 1);
        assertEquals("注文日時: 2026/10/11 09:05", text(html, "date"),
                "注文日時は #temporals.format(orderedAt, 'yyyy/MM/dd HH:mm') で整形してください");
        assertEquals("合計: 3,480円", text(html, "total"),
                "合計は #numbers.formatInteger(total, 1, 'COMMA') で3桁区切りにし、後ろに「円」を付けてください");
        String shipped = render(SHIPPED, 1);
        assertEquals("注文日時: 2026/01/02 18:30", text(shipped, "date"), "月日は2桁(01/02)、時刻は24時間表記(18:30)です");
        assertEquals("合計: 1,234,567円", text(shipped, "total"), "100万以上も3桁ごとに区切ってください");
    }

    @Test
    @DisplayName("キャンセルできる注文には /shop/orders/{id}/cancel のリンクが出る")
    void cancelLink() {
        assertEquals("/shop/orders/1001/cancel", href(render(ORDERED, 1), "cancel"),
                "キャンセルリンクは th:href=\"@{/orders/{id}/cancel(id=*{id})}\" で組み立ててください(/shop は自動で付きます)");
    }

    @Test
    @DisplayName("出荷済み(キャンセル不可)の注文にはキャンセルリンクを出さない")
    void noCancelLinkWhenShipped() {
        String html = render(SHIPPED, 1);
        assertNull(href(html, "cancel"),
                "cancellable() が false の注文にはキャンセルリンクを出さないでください(th:if=\"*{cancellable()}\")");
        assertFalse(html.contains("この注文をキャンセル"), "出荷済みの注文に「この注文をキャンセル」が出ています");
    }

    @Test
    @DisplayName("戻るリンクは /shop/orders?page=… (page の値がクエリになる)")
    void backLink() {
        assertEquals("/shop/orders?page=2", href(render(ORDERED, 2), "back"),
                "戻るリンクは th:href=\"@{/orders(page=${page})}\" で組み立ててください");
        assertEquals("/shop/orders?page=7", href(render(SHIPPED, 7), "back"), "page の値をそのままクエリに使ってください");
    }
}
