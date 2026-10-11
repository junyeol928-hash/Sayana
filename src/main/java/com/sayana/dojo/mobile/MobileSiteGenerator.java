package com.sayana.dojo.mobile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sayana.dojo.ai.LessonContext;
import com.sayana.dojo.ai.PromptLibrary;
import com.sayana.dojo.config.DojoProperties;
import com.sayana.dojo.content.Chapter;
import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.ExerciseDef;
import com.sayana.dojo.content.Lesson;
import com.sayana.dojo.content.MarkdownRenderer;
import com.sayana.dojo.content.Part;

/**
 * スマホ版(オフライン対応の静的サイト)を m/ に書き出す。
 * <p>
 * GitHub Pages で https://<ユーザー>.github.io/Sayana/m/ として配信される。PC もサーバーも要らず、
 * 一度開けば電波が無くても「読む・クイズ・復習・演習の自己チェック」ができる。
 * <pre>
 * ./mvnw -q compile exec:java -Dexec.mainClass=com.sayana.dojo.mobile.MobileSiteGenerator
 * </pre>
 * 教材(content/)を変えたら実行し直す。古いままだと MobileSiteTest が失敗して知らせる。
 */
public final class MobileSiteGenerator {

    /** 画面・スクリプトなど、src/main/resources/mobile/ からそのままコピーするファイル。 */
    static final List<String> STATIC_FILES = List.of("index.html", "app.js", "mobile.css", "icon.svg",
            "manifest.webmanifest");

    /** AI 機能のプロンプト。 */
    static final List<String> PROMPTS = List.of("tutor", "hint", "review", "explain-error");

    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "m");
        Map<String, byte[]> files = new MobileSiteGenerator().build(Path.of("content"));
        write(out, files);
        System.out.println("スマホ版を書き出しました: " + out.toAbsolutePath() + "(" + files.size() + "ファイル)");
    }

    /** 書き出すファイル(m/ からの相対パス → 中身)を作る。 */
    public Map<String, byte[]> build(Path contentDir) throws IOException {
        DojoProperties props = new DojoProperties();
        props.setContentDir(contentDir);
        ContentRepository content = new ContentRepository(props, new MarkdownRenderer());
        PromptLibrary prompts = new PromptLibrary();

        Map<String, byte[]> files = new TreeMap<>();
        for (String name : STATIC_FILES) {
            files.put(name, resource("mobile/" + name));
        }
        // PC版と同じ見た目にするため、CSS は PC版のものをそのまま使う(スマホ用の追加は mobile.css)
        files.put("app.css", resource("static/css/app.css"));

        // 目次
        List<Object> parts = new ArrayList<>();
        for (Part p : content.parts()) {
            List<Object> chapters = new ArrayList<>();
            for (Chapter c : p.chapters()) {
                List<Object> lessons = new ArrayList<>();
                for (Lesson l : c.lessons()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", l.id());
                    m.put("title", l.title());
                    m.put("summary", l.summary());
                    m.put("minutes", l.minutes());
                    m.put("ready", l.ready());
                    m.put("exercises", l.exercises().size());
                    m.put("quiz", l.quiz().size());
                    lessons.add(m);
                }
                chapters.add(ordered("id", c.id(), "title", c.title(), "lessons", lessons));
            }
            parts.add(ordered("id", p.id(), "title", p.title(), "description", p.description(), "chapters", chapters));
        }
        files.put("data/curriculum.json", toJson(ordered("parts", parts)));

        // プロンプト(AI 機能用)
        Map<String, String> promptMap = new LinkedHashMap<>();
        for (String name : PROMPTS) {
            promptMap.put(name, prompts.get(name));
        }
        files.put("data/prompts.json", toJson(promptMap));

        // レッスンごとのデータ
        String common = prompts.get("common");
        for (Lesson l : content.readyLessons()) {
            files.put("data/lessons/" + l.id() + ".json", toJson(lessonData(content, l, common)));
        }

        // Service Worker(全ファイルを先に取っておき、電波が無くても開けるようにする)
        files.put("sw.js", serviceWorker(files));
        return files;
    }

    private Map<String, Object> lessonData(ContentRepository content, Lesson l, String commonPrompt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.id());
        m.put("title", l.title());
        m.put("summary", l.summary());
        m.put("minutes", l.minutes());
        m.put("part", content.part(l.partId()).map(Part::title).orElse(""));
        m.put("chapter", content.chapter(l.chapterId()).map(Chapter::title).orElse(""));
        m.put("prev", content.previous(l.id()).map(Lesson::id).orElse(null));
        m.put("next", content.next(l.id()).map(Lesson::id).orElse(null));
        m.put("goals", l.goals());
        m.put("bodyHtml", l.bodyHtml());
        m.put("samples", l.samples().stream().map(s -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("title", s.title());
            x.put("descriptionHtml", s.descriptionHtml());
            x.put("code", s.code());
            return x;
        }).toList());
        m.put("exercises", l.exercises().stream().map(this::exercise).toList());
        m.put("quiz", l.quiz().stream().map(q -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("questionHtml", q.questionHtml());
            x.put("choicesHtml", q.choicesHtml());
            x.put("answer", q.answer());
            x.put("explanationHtml", q.explanationHtml());
            return x;
        }).toList());
        m.put("columns", l.columns().stream().map(c -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("type", c.type());
            x.put("label", c.label());
            x.put("title", c.title());
            x.put("bodyHtml", c.bodyHtml());
            return x;
        }).toList());
        if (l.sts() != null) {
            Map<String, Object> sts = new LinkedHashMap<>();
            sts.put("introHtml", l.sts().introHtml());
            sts.put("steps", l.sts().steps().stream()
                    .map(s -> ordered("title", s.title(), "detailHtml", s.detailHtml())).toList());
            m.put("sts", sts);
        }
        m.put("checklist", l.checklist().stream().map(c -> ordered("key", c.key(), "text", c.text())).toList());
        // AI に渡す文脈(PC版と同じ文字列。プロンプトキャッシュが効く)
        m.put("aiContext", LessonContext.build(commonPrompt, l));
        return m;
    }

    private Map<String, Object> exercise(ExerciseDef e) {
        Map<String, Object> x = new LinkedHashMap<>();
        x.put("key", e.key());
        x.put("title", e.title());
        x.put("className", e.className());
        x.put("difficulty", e.difficulty());
        x.put("problemHtml", e.problemHtml());
        x.put("problemMarkdown", e.problemMarkdown());
        x.put("starter", e.starter());
        x.put("solution", e.solution());
        x.put("test", e.test());
        x.put("usesSpring", e.usesSpring());
        return x;
    }

    /** 書き出す JSON の項目順を毎回同じにする(Map.of は順序が決まらない)。 */
    private static Map<String, Object> ordered(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    private byte[] serviceWorker(Map<String, byte[]> files) {
        String template = new String(resource("mobile/sw.template.js"), StandardCharsets.UTF_8);
        List<String> list = new ArrayList<>();
        list.add("./");
        list.addAll(files.keySet());
        String fileList = String.join(",\n  ", list.stream().map(f -> "\"" + f + "\"").toList());
        return template.replace("__VERSION__", version(files)).replace("__FILES__", fileList)
                .getBytes(StandardCharsets.UTF_8);
    }

    /** 中身から決まるバージョン(教材が変わればキャッシュも入れ替わる)。 */
    private static String version(Map<String, byte[]> files) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (var e : files.entrySet()) {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update(e.getValue());
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] toJson(Object o) {
        try {
            return (json.writeValueAsString(o) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] resource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("リソースが見つかりません: " + path, e);
        }
    }

    /** out を作り直して書き出す(消えたレッスンのファイルも残さない)。 */
    static void write(Path out, Map<String, byte[]> files) throws IOException {
        if (Files.exists(out)) {
            // 間違ったフォルダを消さないよう、前回書き出したもの(sw.js がある)か空のフォルダだけを作り直す
            boolean generated = Files.exists(out.resolve("sw.js"));
            boolean empty;
            try (Stream<Path> list = Files.list(out)) {
                empty = list.findAny().isEmpty();
            }
            if (!generated && !empty) {
                throw new IllegalStateException("書き出し先にスマホ版以外のファイルがあります: " + out.toAbsolutePath());
            }
            try (Stream<Path> walk = Files.walk(out)) {
                for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
        for (var e : files.entrySet()) {
            Path p = out.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
    }
}
