package com.sayana.dojo.content;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.sayana.dojo.config.DojoProperties;
import com.sayana.dojo.content.Lesson.AtWork;
import com.sayana.dojo.content.Lesson.ChecklistItem;
import com.sayana.dojo.content.Lesson.Column;
import com.sayana.dojo.content.Lesson.QuizQuestion;
import com.sayana.dojo.content.Lesson.ReadingQuestion;
import com.sayana.dojo.content.Lesson.Sample;
import com.sayana.dojo.content.Lesson.StsGuide;
import com.sayana.dojo.content.Lesson.StsStep;

/**
 * content/ 配下の教材(部→章→レッスン)を読み込んで保持する。
 * 起動時に1回読み込み、{@link #reload()} で読み直せる(教材を書きながら確認する用)。
 */
@Component
public class ContentRepository {

    private static final Logger log = LoggerFactory.getLogger(ContentRepository.class);

    private final Path contentDir;
    private final MarkdownRenderer markdown;

    private volatile Snapshot snapshot;

    public ContentRepository(DojoProperties props, MarkdownRenderer markdown) {
        this.contentDir = props.getContentDir();
        this.markdown = markdown;
        reload();
    }

    private record Snapshot(List<Part> parts, Map<String, Lesson> lessons, List<Lesson> ordered,
                            Map<String, ExerciseDef> exercises, List<Term> glossary) {
    }

    public synchronized void reload() {
        if (!Files.isDirectory(contentDir)) {
            throw new IllegalStateException("教材ディレクトリが見つかりません: " + contentDir.toAbsolutePath()
                    + "(プロジェクトのルートで起動してください)");
        }
        List<Part> parts = new ArrayList<>();
        Map<String, Lesson> lessons = new LinkedHashMap<>();
        Map<String, ExerciseDef> exercises = new LinkedHashMap<>();
        int order = 0;
        for (Path partDir : subDirs(contentDir)) {
            Map<String, Object> py = yaml(partDir.resolve("part.yml"));
            String partId = str(py, "id");
            List<Chapter> chapters = new ArrayList<>();
            for (Path chapterDir : subDirs(partDir)) {
                Map<String, Object> cy = yaml(chapterDir.resolve("chapter.yml"));
                String chapterId = partId + "/" + str(cy, "id");
                List<Lesson> chapterLessons = new ArrayList<>();
                for (Path lessonDir : subDirs(chapterDir)) {
                    Lesson lesson = loadLesson(lessonDir, partId, chapterId);
                    if (lessons.putIfAbsent(lesson.id(), lesson) != null) {
                        throw new IllegalStateException("レッスンIDが重複しています: " + lesson.id());
                    }
                    lesson.exercises().forEach(e -> exercises.put(e.key(), e));
                    chapterLessons.add(lesson);
                }
                chapters.add(new Chapter(chapterId, partId, str(cy, "title"), List.copyOf(chapterLessons)));
            }
            parts.add(new Part(partId, ++order, str(py, "title"), str(py, "description"), List.copyOf(chapters)));
        }
        List<Lesson> ordered = parts.stream().flatMap(p -> p.lessons().stream()).toList();
        List<Term> glossary = loadGlossary(lessons);
        this.snapshot = new Snapshot(List.copyOf(parts), Collections.unmodifiableMap(lessons), ordered,
                Collections.unmodifiableMap(exercises), glossary);
        log.info("教材を読み込みました: {}部 / {}レッスン(公開 {}) / 演習 {}問 / 読解 {}問 / 用語 {}語", parts.size(),
                ordered.size(), ordered.stream().filter(Lesson::ready).count(), exercises.size(),
                ordered.stream().mapToInt(l -> l.reading().size()).sum(), glossary.size());
    }

    public List<Part> parts() {
        return snapshot.parts();
    }

    public List<Lesson> allLessons() {
        return snapshot.ordered();
    }

    public List<Lesson> readyLessons() {
        return snapshot.ordered().stream().filter(Lesson::ready).toList();
    }

    /** 用語集(content/glossary.yml の順)。 */
    public List<Term> glossary() {
        return snapshot.glossary();
    }

    public Optional<Lesson> lesson(String id) {
        return Optional.ofNullable(snapshot.lessons().get(id));
    }

    public Optional<ExerciseDef> exercise(String key) {
        return Optional.ofNullable(snapshot.exercises().get(key));
    }

    public List<ExerciseDef> allExercises() {
        return List.copyOf(snapshot.exercises().values());
    }

    public Optional<Part> part(String partId) {
        return snapshot.parts().stream().filter(p -> p.id().equals(partId)).findFirst();
    }

    public Optional<Chapter> chapter(String chapterId) {
        return snapshot.parts().stream().flatMap(p -> p.chapters().stream())
                .filter(c -> c.id().equals(chapterId)).findFirst();
    }

    /** 公開済みレッスンの中での前後。 */
    public Optional<Lesson> previous(String lessonId) {
        return neighbor(lessonId, -1);
    }

    public Optional<Lesson> next(String lessonId) {
        return neighbor(lessonId, +1);
    }

    private Optional<Lesson> neighbor(String lessonId, int delta) {
        List<Lesson> ordered = snapshot.ordered();
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id().equals(lessonId)) {
                int j = i + delta;
                return j >= 0 && j < ordered.size() ? Optional.of(ordered.get(j)) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    // ---- 読み込み ----

    private Lesson loadLesson(Path dir, String partId, String chapterId) {
        Map<String, Object> y = yaml(dir.resolve("lesson.yml"));
        String id = str(y, "id");
        String status = Optional.ofNullable(str(y, "status")).orElse("planned");
        if (!"ready".equals(status)) {
            return new Lesson(id, partId, chapterId, str(y, "title"), status, intVal(y, "minutes", 0),
                    str(y, "summary"), strList(y, "goals"), "", "", List.of(), List.of(), List.of(), null,
                    List.of(), List.of(), List.of(), List.of(), List.of());
        }
        String body = read(dir.resolve("lesson.md"));

        List<Sample> samples = maps(y, "samples").stream()
                .map(m -> new Sample(str(m, "title"), markdown.render(str(m, "description")), strip(str(m, "code")),
                        strList(m, "libraries")))
                .toList();

        List<QuizQuestion> quiz = new ArrayList<>();
        for (Map<String, Object> q : maps(y, "quiz")) {
            List<String> choices = strList(q, "choices");
            int answer = intVal(q, "answer", -1);
            if (answer < 0 || answer >= choices.size()) {
                throw new IllegalStateException(id + ": クイズの answer が選択肢の範囲外です: " + str(q, "question"));
            }
            quiz.add(new QuizQuestion(str(q, "question"), markdown.render(str(q, "question")), choices,
                    choices.stream().map(markdown::renderInline).toList(), answer,
                    markdown.render(str(q, "explanation"))));
        }

        List<Column> columns = maps(y, "columns").stream()
                .map(m -> new Column(Optional.ofNullable(str(m, "type")).orElse("trivia"), str(m, "title"),
                        markdown.render(str(m, "body"))))
                .toList();

        StsGuide sts = null;
        if (y.get("sts") instanceof Map<?, ?> s) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sm = (Map<String, Object>) s;
            sts = new StsGuide(markdown.render(str(sm, "intro")), maps(sm, "steps").stream()
                    .map(m -> new StsStep(str(m, "title"), markdown.render(str(m, "detail")))).toList());
        }

        List<ChecklistItem> checklist = maps(y, "checklist").stream()
                .map(m -> new ChecklistItem(str(m, "key"), str(m, "text"))).toList();

        List<ExerciseDef> exercises = new ArrayList<>();
        Path exDir = dir.resolve("exercises");
        if (Files.isDirectory(exDir)) {
            for (Path e : subDirs(exDir)) {
                exercises.add(loadExercise(e, id));
            }
        }
        List<Lesson.Check> checks = loadChecks(dir, id);
        return new Lesson(id, partId, chapterId, str(y, "title"), status, readingMinutes(body),
                str(y, "summary"), strList(y, "goals"), body, insertChecks(id, markdown.render(body), checks), samples,
                List.copyOf(quiz), columns, sts, checklist, List.copyOf(exercises), loadReading(dir, id),
                loadAtWork(dir), checks);
    }

    /** checks.yml(本文の途中に置く「手を止めて」の問い)。無ければ空。 */
    private List<Lesson.Check> loadChecks(Path dir, String lessonId) {
        Path file = dir.resolve("checks.yml");
        if (!Files.exists(file)) {
            return List.of();
        }
        List<Lesson.Check> list = new ArrayList<>();
        for (Map<String, Object> c : yamlList(file)) {
            List<String> choices = strList(c, "choices");
            int answer = intVal(c, "answer", -1);
            if (choices.size() < 2 || answer < 0 || answer >= choices.size()) {
                throw new IllegalStateException(lessonId + ": checks.yml の選択肢か answer がおかしい: " + str(c, "question"));
            }
            list.add(new Lesson.Check(str(c, "after"), markdown.render(str(c, "question")),
                    choices.stream().map(markdown::renderInline).toList(), answer, markdown.render(str(c, "explanation"))));
        }
        return List.copyOf(list);
    }

    private static final Pattern H2 = Pattern.compile("<h2[^>]*>(.*?)</h2>", Pattern.DOTALL);

    /**
     * 「手を止めて」を、見出し(after)の節の終わり(次の ## の前)に差し込む。PC 版とスマホ版で同じ HTML を使う。
     * 選択肢は問いごとに決まった並びに入れ替える(書いた順のままだと正解の位置が偏るため)。値は元の番号。
     */
    static String insertChecks(String lessonId, String html, List<Lesson.Check> checks) {
        if (checks.isEmpty()) {
            return html;
        }
        List<int[]> sections = new ArrayList<>(); // [見出しの始まり, 節の終わり]
        List<String> titles = new ArrayList<>();
        Matcher m = H2.matcher(html);
        while (m.find()) {
            if (!sections.isEmpty()) {
                sections.get(sections.size() - 1)[1] = m.start();
            }
            sections.add(new int[] {m.start(), html.length()});
            // 見出しの HTML(<code> や &amp; など)を、checks.yml に書く素の文字に戻して比べる
            titles.add(headingKey(org.springframework.web.util.HtmlUtils.htmlUnescape(m.group(1).replaceAll("<[^>]+>", ""))));
        }
        // 後ろから差し込む(前の位置がずれないように)。同じ節に 2 問あれば書いた順に並べる
        java.util.TreeMap<Integer, StringBuilder> inserts = new java.util.TreeMap<>();
        for (int i = 0; i < checks.size(); i++) {
            Lesson.Check c = checks.get(i);
            String key = headingKey(c.after() == null ? "" : c.after());
            int at = titles.indexOf(key);
            if (at < 0) {
                throw new IllegalStateException(lessonId + ": checks.yml の after に合う ## 見出しがありません: " + c.after());
            }
            if (titles.lastIndexOf(key) != at) {
                throw new IllegalStateException(lessonId + ": 同じ ## 見出しが 2 つあり、checks.yml の after でどちらか決められません: " + c.after());
            }
            inserts.computeIfAbsent(sections.get(at)[1], k -> new StringBuilder()).append(checkHtml(lessonId, i, c));
        }
        StringBuilder out = new StringBuilder(html);
        for (var e : inserts.descendingMap().entrySet()) {
            out.insert(e.getKey(), e.getValue());
        }
        return out.toString();
    }

    /** 見出しの比べ方: Markdown の記号(` と **)を外し、前後の空白を落とす。 */
    private static String headingKey(String s) {
        return s.replace("`", "").replace("**", "").strip();
    }

    private static String checkHtml(String lessonId, int index, Lesson.Check c) {
        List<Integer> order = new ArrayList<>(java.util.stream.IntStream.range(0, c.choicesHtml().size()).boxed().toList());
        Collections.shuffle(order, new java.util.Random((lessonId + "!" + index).hashCode()));
        StringBuilder b = new StringBuilder();
        b.append("<aside class=\"check\" data-check=\"").append(index).append("\" data-answer=\"").append(c.answer())
                .append("\" aria-labelledby=\"check-").append(index).append("\">\n<p class=\"check-label\" id=\"check-")
                .append(index).append("\">手を止めて</p>\n<div class=\"check-q\">").append(c.questionHtml())
                .append("</div>\n<div class=\"check-choices\" role=\"group\" aria-label=\"選択肢\">");
        for (int j : order) {
            b.append("<button type=\"button\" class=\"choice\" data-c=\"").append(j).append("\"><span>")
                    .append(c.choicesHtml().get(j)).append("</span></button>");
        }
        b.append("</div>\n<div class=\"check-explain\" hidden>").append(c.explanationHtml()).append("</div>\n</aside>\n");
        return b.toString();
    }

    /**
     * 本文を読む目安(分)。手で書いた値は全レッスン同じ 15 分になっていて当てにならないので、本文から数える。
     * 文章は 1 分に 500 字、コードは 1 分に 12 行(空行を除く)で読む前提。
     */
    static int readingMinutes(String body) {
        int codeLines = 0;
        StringBuilder prose = new StringBuilder();
        boolean inCode = false;
        for (String line : body.split("\\R")) {
            if (line.strip().startsWith("```")) {
                inCode = !inCode;
            } else if (inCode) {
                if (!line.isBlank()) {
                    codeLines++;
                }
            } else {
                prose.append(line.replaceAll("[\\s#>*|`\\-]", ""));
            }
        }
        return Math.max(1, (int) Math.ceil(prose.length() / 500.0 + codeLines / 12.0));
    }

    /** reading.yml(コード読解問題のリスト)。無ければ空。 */
    private List<ReadingQuestion> loadReading(Path dir, String lessonId) {
        Path file = dir.resolve("reading.yml");
        if (!Files.exists(file)) {
            return List.of();
        }
        List<ReadingQuestion> list = new ArrayList<>();
        for (Map<String, Object> r : yamlList(file)) {
            List<String> choices = strList(r, "choices");
            int answer = intVal(r, "answer", -1);
            if (answer < 0 || answer >= choices.size()) {
                throw new IllegalStateException(lessonId + ": 読解問題の answer が選択肢の範囲外です: " + str(r, "title"));
            }
            String question = Optional.ofNullable(str(r, "question")).orElse("出力は?");
            list.add(new ReadingQuestion(str(r, "title"), intVal(r, "level", 1), strip(str(r, "code")),
                    markdown.renderInline(question), choices.stream().map(markdown::renderInline).toList(), answer,
                    markdown.renderUntrusted(str(r, "explanation")),
                    strList(r, "trace").stream().map(markdown::renderInline).toList()));
        }
        return List.copyOf(list);
    }

    /** atwork.yml(「仕事のコードではこう出てくる」のリスト)。無ければ空。 */
    private List<AtWork> loadAtWork(Path dir) {
        Path file = dir.resolve("atwork.yml");
        if (!Files.exists(file)) {
            return List.of();
        }
        List<AtWork> list = new ArrayList<>();
        for (Map<String, Object> a : yamlList(file)) {
            Map<Integer, String> notes = new TreeMap<>();
            if (a.get("notes") instanceof Map<?, ?> n) {
                n.forEach((k, v) -> notes.put(Integer.parseInt(k.toString()), markdown.renderInline(v.toString())));
            }
            list.add(new AtWork(str(a, "title"), markdown.renderUntrusted(str(a, "intro")), strip(str(a, "code")),
                    Collections.unmodifiableMap(notes),
                    strList(a, "points").stream().map(markdown::renderInline).toList()));
        }
        return List.copyOf(list);
    }

    /** content/glossary.yml(用語集)。無ければ空。 */
    private List<Term> loadGlossary(Map<String, Lesson> lessons) {
        Path file = contentDir.resolve("glossary.yml");
        if (!Files.exists(file)) {
            return List.of();
        }
        List<Term> terms = new ArrayList<>();
        for (Map<String, Object> t : yamlList(file)) {
            String lessonId = str(t, "lesson");
            if (lessonId != null && !lessons.containsKey(lessonId)) {
                throw new IllegalStateException("用語集の lesson が存在しません: " + str(t, "term") + " → " + lessonId);
            }
            terms.add(new Term(str(t, "term"), str(t, "read"), str(t, "en"), markdown.renderInline(str(t, "def")),
                    lessonId));
        }
        return List.copyOf(terms);
    }

    private ExerciseDef loadExercise(Path dir, String lessonId) {
        Map<String, Object> y = yaml(dir.resolve("exercise.yml"));
        String problem = str(y, "problem");
        return new ExerciseDef(lessonId + "/" + dir.getFileName(), lessonId, str(y, "title"), str(y, "className"),
                intVal(y, "difficulty", 1), problem, markdown.render(problem), read(dir.resolve("starter.java")),
                read(dir.resolve("solution.java")), read(dir.resolve("test.java")), strList(y, "libraries"));
    }

    private static List<Path> subDirs(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("教材ファイルを読めません: " + file, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(Path file) {
        Object o = new Yaml(new SafeConstructor(new LoaderOptions())).load(read(file));
        if (!(o instanceof Map)) {
            throw new IllegalStateException("YAML の形式が不正です: " + file);
        }
        return (Map<String, Object>) o;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private static String strip(String s) {
        return s == null ? "" : s.stripTrailing() + "\n";
    }

    private static int intVal(Map<String, Object> m, String key, int def) {
        return m.get(key) instanceof Number n ? n.intValue() : def;
    }

    private static List<String> strList(Map<String, Object> m, String key) {
        if (m.get(key) instanceof List<?> l) {
            return l.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> yamlList(Path file) {
        Object o = new Yaml(new SafeConstructor(new LoaderOptions())).load(read(file));
        if (!(o instanceof List<?> l)) {
            throw new IllegalStateException("YAML はリストで書いてください: " + file);
        }
        return l.stream().filter(Map.class::isInstance).map(x -> (Map<String, Object>) x).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Map<String, Object> m, String key) {
        if (m.get(key) instanceof List<?> l) {
            return l.stream().filter(Map.class::isInstance).map(x -> (Map<String, Object>) x).toList();
        }
        return List.of();
    }
}
