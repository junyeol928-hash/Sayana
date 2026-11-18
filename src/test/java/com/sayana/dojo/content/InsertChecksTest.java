package com.sayana.dojo.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InsertChecksTest {

    private static final String HTML = """
            <h2>if の基本</h2>
            <p>インデントだけ見ると……</p>
            <pre><code class="language-text">5以下
            </code></pre>
            <div class="talk"><p><code>x</code> は 7 なのに</p></div>
            <h2>switch</h2>
            <p>フォールスルー</p>
            """;

    private static Lesson.Check check(String after, String before, List<String> why) {
        return check(after, before, 1, why);
    }

    private static Lesson.Check check(String after, String before, int hides, List<String> why) {
        return new Lesson.Check(after, before, hides, "<p>問い</p>", List.of("A", "B", "C"), 1, "<p>理由</p>", why);
    }

    @Test
    @DisplayName("after はその節の終わり(次の見出しの前)に、before は書き出しが合う塊の直前に置く")
    void placesAfterSectionAndBeforeBlock() {
        String out = ContentRepository.insertChecks("t", HTML,
                List.of(check("if の基本", null, List.of()), check(null, "5以下", List.of())));
        int predict = out.indexOf("data-check=\"1\"");
        int recall = out.indexOf("data-check=\"0\"");
        assertThat(predict).isGreaterThan(out.indexOf("インデントだけ")).isLessThan(out.indexOf("<pre>"));
        assertThat(recall).isGreaterThan(out.indexOf("は 7 なのに")).isLessThan(out.indexOf("<h2>switch"));
    }

    @Test
    @DisplayName("before は ` や <code> を外した字で比べ、入れ子の段落ではなく一番外側の塊の前に置く")
    void beforeMatchesTopLevelBlockText() {
        String out = ContentRepository.insertChecks("t", HTML, List.of(check(null, "`x` は 7", List.of())));
        assertThat(out.indexOf("<aside")).isLessThan(out.indexOf("<div class=\"talk\">"));
        assertThat(out).contains("</aside>\n<div class=\"check-gate\" data-gate=\"0\">\n<div class=\"talk\">");
    }

    @Test
    @DisplayName("誤答ごとの 1 文は選んだ選択肢の番号で引けるように、正解の所は出さない。選択肢は問いと結ぶ")
    void rendersWhyPerWrongChoice() {
        String out = ContentRepository.insertChecks("t", HTML, List.of(check("switch", null, List.of("A の読み違い", "", "C の読み違い"))));
        assertThat(out).contains("<p class=\"check-why\" data-for=\"0\" hidden>A の読み違い</p>")
                .contains("<p class=\"check-why\" data-for=\"2\" hidden>C の読み違い</p>")
                .doesNotContain("data-for=\"1\"")
                .contains("aria-labelledby=\"check-0-q\"")
                .contains("<span class=\"choice-dot\" aria-hidden=\"true\"></span>")
                .contains("<div class=\"check-explain\" tabindex=\"-1\" hidden>");
    }

    @Test
    @DisplayName("before は見出しには当てない(見出しの前に置くのは after で書く)")
    void beforeSkipsHeadings() {
        assertThatThrownBy(() -> ContentRepository.insertChecks("t", HTML, List.of(check(null, "switch", List.of()))))
                .hasMessageContaining("本文にありません");
        String out = ContentRepository.insertChecks("t", HTML + "<p>switch 式の続き</p>\n", List.of(check(null, "switch", List.of())));
        assertThat(out).contains("</aside>\n<div class=\"check-gate\" data-gate=\"0\">\n<p>switch 式の続き</p>").doesNotContain("</aside>\n<h2>switch");
    }

    @Test
    @DisplayName("before の問いは、後ろの hides 個の塊を畳みの箱で包む(答えが同じ画面に見えないように)。次の見出しは越えない")
    void gatesBlocksAfterPrediction() {
        String out = ContentRepository.insertChecks("t", HTML, List.of(check(null, "インデントだけ", 2, List.of())));
        String gate = out.substring(out.indexOf("<div class=\"check-gate\" data-gate=\"0\">"), out.indexOf("<div class=\"talk\">"));
        assertThat(gate).contains("インデントだけ見ると").contains("5以下").contains("</pre></div>");
        String all = ContentRepository.insertChecks("t", HTML, List.of(check(null, "インデントだけ", 5, List.of())));
        // 吹き出しの閉じのすぐ後ろで畳みを閉じ、次の見出しは包まない
        assertThat(all).contains("</p></div></div>");
        assertThat(all.indexOf("</p></div></div>")).isLessThan(all.indexOf("<h2>switch</h2>"));
    }

    @Test
    @DisplayName("塊の数え方: 自己終了タグ・SVG・コメントの中のタグ・余分な閉じタグで深さがずれない")
    void topBlocksSurvivesOddMarkup() {
        String html = "<p>a<br>b</p>\n<!-- <div> -->\n</p>\n<figure class=\"figure\"><svg><path d=\"M0 0\"/><g><circle r=\"1\"/></g></svg></figure>\n<p>最後の段落</p>\n";
        List<int[]> blocks = ContentRepository.topBlocks(html);
        assertThat(blocks).hasSize(3);
        assertThat(html.substring(blocks.get(2)[0], blocks.get(2)[1])).isEqualTo("<p>最後の段落</p>");
    }

    @Test
    @DisplayName("before に合う塊が無いとき・2 つあるときは、起動時に分かるようにエラーにする")
    void beforeMustMatchExactlyOneBlock() {
        assertThatThrownBy(() -> ContentRepository.insertChecks("t", HTML, List.of(check(null, "どこにもない文", List.of()))))
                .hasMessageContaining("before で始まる塊が本文にありません");
        String twice = HTML + "<p>フォールスルー の続き</p>\n";
        assertThatThrownBy(() -> ContentRepository.insertChecks("t", twice, List.of(check(null, "フォールスルー", List.of()))))
                .hasMessageContaining("2 つあり");
    }

    @Test
    @DisplayName("before の問いが直前の本文と同じコードを持つなら、本文のコードは出さない(同じコードを 2 回続けない)")
    void dropsBodyCodeRepeatedInQuestion() {
        String html = """
                <h2>if</h2>
                <p>次のコードは何を出す?</p>
                <pre><code class="language-java">int x = 7;
                if (x &gt; 5) { }
                </code></pre>
                <p>答えは……</p>
                """;
        var withCode = new Lesson.Check(null, "答えは", 1,
                "<p>出力は?</p>\n<pre><code class=\"language-java\">int x = 7;\nif (x &gt; 5) { }\n</code></pre>",
                List.of("A", "B"), 0, "<p>理由</p>", List.of());
        String out = ContentRepository.insertChecks("t", html, List.of(withCode));
        assertThat(out.split("int x = 7;", -1)).hasSize(2); // 問いの中の 1 回だけ
        assertThat(out.indexOf("次のコードは")).isLessThan(out.indexOf("<aside"));
        assertThat(out).contains("<div class=\"check-gate\" data-gate=\"0\">\n<p>答えは");

        var otherCode = new Lesson.Check(null, "答えは", 1, "<pre><code>int y = 1;\n</code></pre>",
                List.of("A", "B"), 0, "<p>理由</p>", List.of());
        assertThat(ContentRepository.insertChecks("t", html, List.of(otherCode))).contains("int x = 7;");
    }
}
