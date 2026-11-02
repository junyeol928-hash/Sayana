package com.sayana.dojo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.sayana.dojo.config.DojoProperties;

/**
 * 見出しの書体は使う文字だけに絞って同梱している(scripts/subset-heading-font.py)。
 * 題に新しい字が入ると、その字だけ別の書体で出てしまうので、ここで気づけるようにする。
 */
class HeadingFontTest {

    @Test
    @DisplayName("部・章・レッスンの題の字が、すべて見出しの書体に入っている")
    void titlesAreCovered() throws IOException {
        String chars;
        try (InputStream in = new ClassPathResource("static/css/fonts/heading-chars.txt").getInputStream()) {
            chars = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        ContentRepository content = new ContentRepository(new DojoProperties(), new MarkdownRenderer());
        List<String> titles = new ArrayList<>();
        content.parts().forEach(p -> {
            titles.add(p.title());
            p.chapters().forEach(c -> {
                titles.add(c.title());
                c.lessons().forEach(l -> titles.add(l.title()));
            });
        });
        TreeSet<String> missing = new TreeSet<>();
        for (String t : titles) {
            t.codePoints().filter(c -> chars.indexOf(c) < 0).forEach(c -> missing.add(Character.toString(c)));
        }
        assertThat(missing).as("書体にない字。python scripts/subset-heading-font.py で作り直す").isEmpty();
    }
}
