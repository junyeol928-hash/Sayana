package com.sayana.dojo.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownRendererTest {

    private final MarkdownRenderer md = new MarkdownRenderer();

    @Test
    void 選択肢の型引数はタグにならずそのまま表示される() {
        assertThat(md.renderInline("List<String> a = new ArrayList<>();"))
                .isEqualTo("List&lt;String&gt; a = new ArrayList&lt;&gt;();");
        assertThat(md.renderInline("`Optional<Dept>` を返す")).isEqualTo("<code>Optional&lt;Dept&gt;</code> を返す");
    }

    @Test
    void 掛け合いとコールアウトの拡張記法() {
        String html = md.render("""
                ::: sensei
                **DI** とは
                :::

                ::: warn 全角スペース
                気をつけて
                :::
                """);
        assertThat(html).contains("talk-sensei").contains("<strong>DI</strong>")
                .contains("callout-warn").contains("よくあるミス:全角スペース");
    }

    @Test
    void AIの出力のHTMLはエスケープする() {
        assertThat(md.renderUntrusted("注意 <script>alert(1)</script> と **太字**"))
                .contains("&lt;script&gt;").contains("<strong>太字</strong>");
    }
}
