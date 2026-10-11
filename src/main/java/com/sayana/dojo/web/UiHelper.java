package com.sayana.dojo.web;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.sayana.dojo.content.MarkdownRenderer;

/**
 * テンプレートから使う小さな道具(テンプレートでは {@code ${ui.xxx(...)}})。
 * Thymeleaf 3.1 は式の中での Bean 参照や static アクセスを制限するので、ここに集める。
 */
@Component
public class UiHelper {

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("M/d HH:mm");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm");

    private final MarkdownRenderer markdown;
    private final Clock clock;

    public UiHelper(MarkdownRenderer markdown, Clock clock) {
        this.markdown = markdown;
        this.clock = clock;
    }

    /** AI の出力など信頼できない Markdown を HTML に(生HTMLはエスケープ)。 */
    public String md(String text) {
        return markdown.renderUntrusted(text);
    }

    public String shortTime(Instant instant) {
        return instant == null ? "" : SHORT.format(instant.atZone(clock.getZone()));
    }

    public String longTime(Instant instant) {
        return instant == null ? "" : LONG.format(instant.atZone(clock.getZone()));
    }
}
