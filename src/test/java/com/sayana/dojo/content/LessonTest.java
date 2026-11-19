package com.sayana.dojo.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LessonTest {

    @Test
    void 冒頭の要約は英字とチップと閉じ括弧の直後の助詞を行頭に出さない() {
        // 「素の Java / で体験してから」と、要約の行が助詞で始まっていた(7 / 55。30 回目の審査)
        assertThat(Lesson.joinParticles("素の Java で体験し、<code>@Bean</code> と書く。StringBuilderでの連結(DI)を"))
                .isEqualTo("素の Java&nbsp;で体験し、<code>@Bean</code>&nbsp;と書く。StringBuilder\u2060での連結(DI)\u2060を");
        // 和字の前や句読点の前はそのまま
        assertThat(Lesson.joinParticles("Java 入門。<code>x</code>、")).isEqualTo("Java 入門。<code>x</code>、");
    }
}
