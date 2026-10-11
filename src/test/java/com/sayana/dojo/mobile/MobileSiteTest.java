package com.sayana.dojo.mobile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MobileSiteTest {

    @Test
    @DisplayName("m/(スマホ版)が教材と同じ内容になっている。違ったら ./mvnw -q compile exec:java で作り直す")
    void committedSiteIsUpToDate() throws Exception {
        Map<String, byte[]> expected = new MobileSiteGenerator().build(Path.of("content"));
        Path site = Path.of("m");
        List<String> stale = new ArrayList<>();
        for (var e : expected.entrySet()) {
            Path p = site.resolve(e.getKey());
            if (!Files.exists(p) || !java.util.Arrays.equals(Files.readAllBytes(p), e.getValue())) {
                stale.add(e.getKey());
            }
        }
        try (Stream<Path> walk = Files.walk(site)) {
            walk.filter(Files::isRegularFile).map(p -> site.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> !expected.containsKey(p)).forEach(p -> stale.add(p + "(不要)"));
        }
        assertThat(stale).as("スマホ版が古いままです。./mvnw -q compile exec:java を実行してコミットしてください").isEmpty();
    }

    @Test
    @DisplayName("全レッスンのデータと、オフライン用にすべてのファイルを保存する Service Worker ができる")
    void buildsAllLessons(@TempDir Path tmp) throws Exception {
        Map<String, byte[]> files = new MobileSiteGenerator().build(Path.of("content"));
        assertThat(files.keySet().stream().filter(k -> k.startsWith("data/lessons/"))).hasSize(50);
        String sw = new String(files.get("sw.js"), StandardCharsets.UTF_8);
        assertThat(sw).doesNotContain("__VERSION__").doesNotContain("__FILES__")
                .contains("\"data/lessons/java-hello.json\"").contains("\"index.html\"");
        String lesson = new String(files.get("data/lessons/spring-di.json"), StandardCharsets.UTF_8);
        assertThat(lesson).contains("\"aiContext\"").contains("<lesson id=\\\"spring-di\\\">");
        // APIキーなどの秘密情報が混ざっていないこと
        files.forEach((name, bytes) -> assertThat(new String(bytes, StandardCharsets.UTF_8)).as(name).doesNotContain("sk-ant-api"));
        MobileSiteGenerator.write(tmp.resolve("m"), files);
        assertThat(tmp.resolve("m/index.html")).exists();
    }

    @Test
    @DisplayName("スマホ版以外のファイルがあるフォルダには書き出さない(消してしまわない)")
    void refusesToOverwriteOtherFolders(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("important.txt"), "消さないで");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> MobileSiteGenerator.write(tmp, Map.of("index.html", new byte[0])));
        assertThat(tmp.resolve("important.txt")).exists();
    }

    @Test
    @DisplayName(".gitignore がスマホ版のデータ(m/data)を無視していない(ルートの /data/ だけを無視する)")
    void gitignoreKeepsMobileData() throws Exception {
        List<String> lines = Files.readAllLines(Path.of(".gitignore"));
        assertThat(lines).doesNotContain("data/", "data", "**/data/");
        assertThat(lines).contains("/data/");
    }
}
