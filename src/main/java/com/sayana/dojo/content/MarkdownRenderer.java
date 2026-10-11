package com.sayana.dojo.content;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Markdown → HTML。
 * <p>
 * 教材用({@link #render})は {@code ::: sensei} などの拡張ブロックと生HTMLを許可する。
 * AI の出力など信頼できないテキスト用({@link #renderUntrusted})は HTML をエスケープする。
 */
@Component
public class MarkdownRenderer {

    private static final Pattern BLOCK_START = Pattern.compile("^:::\\s*([a-z]+)\\s*(.*)$");
    private static final Pattern BLOCK_END = Pattern.compile("^:::\\s*$");
    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~).*$");

    private final Parser parser;
    private final HtmlRenderer trusted;
    private final HtmlRenderer untrusted;

    public MarkdownRenderer() {
        List<Extension> extensions = List.of(TablesExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.trusted = HtmlRenderer.builder().extensions(extensions).build();
        this.untrusted = HtmlRenderer.builder().extensions(extensions)
                .escapeHtml(true).sanitizeUrls(true).build();
    }

    /** 教材の Markdown を HTML にする(拡張ブロック対応)。 */
    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        StringBuilder plain = new StringBuilder();
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        boolean inFence = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (FENCE.matcher(line).matches()) {
                inFence = !inFence;
            }
            Matcher start = inFence ? null : BLOCK_START.matcher(line);
            if (start != null && start.matches()) {
                // ::: kind title ... ::: を探す
                int end = findBlockEnd(lines, i + 1);
                if (end > 0) {
                    html.append(trusted.render(parser.parse(plain.toString())));
                    plain.setLength(0);
                    String inner = String.join("\n", java.util.Arrays.copyOfRange(lines, i + 1, end));
                    html.append(renderBlock(start.group(1), start.group(2).trim(), render(inner)));
                    i = end;
                    continue;
                }
            }
            plain.append(line).append('\n');
        }
        html.append(trusted.render(parser.parse(plain.toString())));
        return html.toString();
    }

    /**
     * クイズの選択肢など1行のMarkdownを、外側の &lt;p&gt; なしで HTML にする。
     * 選択肢には「List&lt;String&gt;」のようなコードを地の文で書きがちなので、生HTMLはエスケープする。
     */
    public String renderInline(String markdown) {
        String html = untrusted.render(parser.parse(markdown == null ? "" : markdown)).trim();
        if (html.startsWith("<p>") && html.endsWith("</p>") && html.indexOf("<p>", 1) < 0) {
            html = html.substring(3, html.length() - 4);
        }
        return html;
    }

    /** AI の出力など、信頼できない Markdown を HTML にする(生HTMLはエスケープ)。 */
    public String renderUntrusted(String markdown) {
        if (markdown == null) {
            return "";
        }
        return untrusted.render(parser.parse(markdown));
    }

    private static int findBlockEnd(String[] lines, int from) {
        boolean inFence = false;
        int depth = 0;
        for (int j = from; j < lines.length; j++) {
            if (FENCE.matcher(lines[j]).matches()) {
                inFence = !inFence;
            }
            if (inFence) {
                continue;
            }
            if (BLOCK_START.matcher(lines[j]).matches()) {
                depth++;
            } else if (BLOCK_END.matcher(lines[j]).matches()) {
                if (depth == 0) {
                    return j;
                }
                depth--;
            }
        }
        return -1;
    }

    private static String renderBlock(String kind, String title, String innerHtml) {
        String t = HtmlUtils.htmlEscape(title);
        return switch (kind) {
            case "sensei" -> talk("sensei", "☕", "モカ先輩", innerHtml);
            case "haru" -> talk("haru", "🐣", "ハル", innerHtml);
            case "point" -> callout("point", "💡", t.isEmpty() ? "ポイント" : t, innerHtml);
            case "warn" -> callout("warn", "⚠️", t.isEmpty() ? "よくあるミス" : "よくあるミス:" + t, innerHtml);
            case "genba" -> callout("genba", "🏢", t.isEmpty() ? "現場あるある" : "現場あるある:" + t, innerHtml);
            case "trivia" -> callout("trivia", "☕", t.isEmpty() ? "豆知識" : "豆知識:" + t, innerHtml);
            default -> callout("note", "📝", t.isEmpty() ? "メモ" : t, innerHtml);
        };
    }

    private static String talk(String who, String icon, String name, String innerHtml) {
        return """
                <div class="talk talk-%s"><div class="talk-avatar"><span class="talk-icon">%s</span><span class="talk-name">%s</span></div><div class="talk-bubble">%s</div></div>
                """.formatted(who, icon, name, innerHtml);
    }

    private static String callout(String kind, String icon, String title, String innerHtml) {
        return """
                <aside class="callout callout-%s"><div class="callout-title">%s %s</div>%s</aside>
                """.formatted(kind, icon, title, innerHtml);
    }
}
