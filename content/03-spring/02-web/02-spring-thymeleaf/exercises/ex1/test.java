import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

class ProductListPageTest {

    /** ProductListPage.TEMPLATE を products を渡して描画する */
    private static String render(List<Product> products) {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        Context ctx = new Context(Locale.JAPAN);
        ctx.setVariable("products", products);
        return engine.process(ProductListPage.TEMPLATE, ctx);
    }

    /** <td class="xxx">内容</td> の内容を上から順に取り出す */
    private static List<String> cells(String html, String cls) {
        Matcher m = Pattern.compile("<td class=\"" + cls + "\"[^>]*>\\s*(.*?)\\s*</td>", Pattern.DOTALL).matcher(html);
        List<String> list = new ArrayList<>();
        while (m.find()) {
            list.add(m.group(1));
        }
        return list;
    }

    private static int countRows(String html) {
        Matcher m = Pattern.compile("<tr class=\"item\"").matcher(html);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static final List<Product> THREE = List.of(
            new Product("ボールペン", 1200, 12),
            new Product("ノート", 98, 0),
            new Product("高級万年筆", 1234567, 1));

    @Test
    @DisplayName("商品の数だけ <tr class=\"item\"> が繰り返され、No. は 1, 2, 3")
    void rowsAndNumbers() {
        String html = render(THREE);
        assertEquals(3, countRows(html),
                "商品3件なら <tr class=\"item\"> は3行のはずです。th:each を付け、ダミー行は消すか th:remove=\"all\" にしてください。\n描画結果:\n" + html);
        assertEquals(List.of("1", "2", "3"), cells(html, "no"),
                "No. はステータス変数の count(1始まり)を th:text で出してください(index は0始まり)");
    }

    @Test
    @DisplayName("商品名が順に表示され、HTML のタグはエスケープされる")
    void namesAreEscaped() {
        String html = render(List.of(new Product("<特価>ノート", 100, 5), new Product("消しゴム", 80, 3)));
        assertEquals(List.of("&lt;特価&gt;ノート", "消しゴム"), cells(html, "name"),
                "商品名は th:text で出してください。th:utext だと <特価> がタグとして解釈されてしまいます");
    }

    @Test
    @DisplayName("価格は3桁区切り+円(98円 / 1,200円 / 1,234,567円)")
    void prices() {
        assertEquals(List.of("1,200円", "98円", "1,234,567円"), cells(render(THREE), "price"),
                "価格は ${#numbers.formatInteger(p.price, 1, 'COMMA')} + '円' の形で出してください");
    }

    @Test
    @DisplayName("在庫 0 は「売り切れ」、在庫 1 以上は「残り○個」")
    void stock() {
        assertEquals(List.of("残り12個", "売り切れ", "残り1個"), cells(render(THREE), "stock"),
                "在庫セルは各行に1つだけ出してください。在庫0は th:if で「売り切れ」、それ以外は th:unless で「残り○個」です");
    }

    @Test
    @DisplayName("商品があるときは「商品がありません」を出さない")
    void notEmptyMessage() {
        String html = render(THREE);
        assertFalse(html.contains("商品がありません"),
                "商品があるときは <p class=\"empty\"> を出さないでください(th:if で products が空のときだけにする)");
    }

    @Test
    @DisplayName("商品が0件なら表を出さず「商品がありません」だけ")
    void empty() {
        String html = render(List.of());
        assertTrue(html.contains("商品がありません"), "0件のときは「商品がありません」を出してください");
        assertFalse(html.contains("<table"),
                "0件のときは <table> ごと出さないでください(<table> に th:unless を付けます)\n描画結果:\n" + html);
    }
}
