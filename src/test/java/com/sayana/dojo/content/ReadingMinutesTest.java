package com.sayana.dojo.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadingMinutesTest {

    @Test
    @DisplayName("文章は 500 字、コードは空行を除いて 12 行で 1 分と数え、端数は切り上げる")
    void countsProseAndCode() {
        String prose = "あ".repeat(750);
        String code = "```java\n" + "int x = 1;\n".repeat(6) + "\n\n```\n";
        // 750 / 500 + 6 / 12 = 2.0
        assertThat(ContentRepository.readingMinutes(prose + "\n" + code)).isEqualTo(2);
        assertThat(ContentRepository.readingMinutes(prose + "い" + "\n" + code)).isEqualTo(3);
    }

    @Test
    @DisplayName("見出しや箇条書きの記号は字数に入れず、空の本文でも 1 分以上")
    void ignoresMarkupAndHasFloor() {
        assertThat(ContentRepository.readingMinutes("## 見出し\n- 項目\n")).isEqualTo(1);
        assertThat(ContentRepository.readingMinutes("")).isEqualTo(1);
    }
}
