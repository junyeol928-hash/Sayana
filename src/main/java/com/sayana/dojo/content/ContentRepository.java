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
                    markdown.render(str(q, "explanation")),
                    whyHtml(q, choices.size(), answer, id + " のクイズ: " + str(q, "question"))));
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
            if ((c.get("after") == null) == (c.get("before") == null)) {
                throw new IllegalStateException(lessonId + ": checks.yml は after か before のどちらか 1 つで置き場所を書く: " + str(c, "question"));
            }
            int hides = intVal(c, "hides", 1);
            if (hides < 1 || hides > 5) {
                throw new IllegalStateException(lessonId + ": checks.yml の hides は 1〜5: " + str(c, "question"));
            }
            list.add(new Lesson.Check(str(c, "after"), str(c, "before"), hides, markdown.render(str(c, "question")),
                    choices.stream().map(markdown::renderInline).toList(), answer, markdown.render(str(c, "explanation")),
                    whyHtml(c, choices.size(), answer, lessonId + " の checks.yml: " + str(c, "question"))));
        }
        return List.copyOf(list);
    }

    /**
     * why(選んだ誤答に向けた 1 文)。選択肢と同じ並びで、正解の所は空。書いていない問いは空のリスト(誤答の 1 文を出さない)。
     * 手を止めて・クイズ・読解で同じ書き方。
     */
    private List<String> whyHtml(Map<String, Object> m, int choices, int answer, String where) {
        Object raw = m.get("why");
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> w)) {
            throw new IllegalStateException("why は選択肢ごとのリストで書く: " + where);
        }
        List<String> why = w.stream().map(x -> x == null ? "" : String.valueOf(x)).toList();
        if (why.size() != choices) {
            throw new IllegalStateException("why は choices と同じ数にする: " + where);
        }
        if (!why.get(answer).isBlank()) {
            throw new IllegalStateException("why の正解の所は空(\"\")にする: " + where);
        }
        return why.stream().map(x -> x.isBlank() ? "" : markdown.renderInline(x)).toList();
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
        List<int[]> blocks = null; // before を使う問いがあるときだけ数える
        // 後ろから差し込む(前の位置がずれないように)。同じ所に 2 問あれば書いた順に並べる。
        // 同じ所では、前の畳みの閉じ(</div>)を、次の問いより先に置く
        TreeMap<Integer, StringBuilder> inserts = new TreeMap<>();
        TreeMap<Integer, StringBuilder> closes = new TreeMap<>();
        TreeMap<Integer, Integer> drops = new TreeMap<>(); // 出さない本文の塊 [始まり → 終わり]
        for (int i = 0; i < checks.size(); i++) {
            Lesson.Check c = checks.get(i);
            int pos;
            if (c.before() != null) {
                if (blocks == null) {
                    blocks = topBlocks(html);
                }
                pos = beforePosition(lessonId, html, blocks, c.before());
                // 答えが同じ画面に見えないよう、後ろの hides 個の塊を畳みの箱で包む(次の見出しは越えない)。
                // 畳むのはスマホ・PC の JS(JS が動かないときは開いたまま)
                int first = 0;
                while (blocks.get(first)[0] != pos) {
                    first++;
                }
                int last = first;
                while (last + 1 < blocks.size() && last + 1 - first < c.hides()
                        && !HEADING.matcher(html).region(blocks.get(last + 1)[0], blocks.get(last + 1)[1]).lookingAt()) {
                    last++;
                }
                inserts.computeIfAbsent(pos, k -> new StringBuilder()).append(checkHtml(lessonId, i, c))
                        .append("<div class=\"check-gate\" data-gate=\"").append(i).append("\">\n");
                closes.computeIfAbsent(blocks.get(last)[1], k -> new StringBuilder()).append("</div>\n");
                // 問いの中に、本文で直前にあるコードと同じコードがあれば、本文のほうは出さない(同じコードが続けて 2 回出ないように。
                // 問いの枠の中のコードが本文を兼ねる。復習では本文が無くても問いだけで答えられるよう、問いにコードを入れている)
                if (first > 0 && sameCode(html, blocks.get(first - 1), c.questionHtml())) {
                    drops.put(blocks.get(first - 1)[0], blocks.get(first - 1)[1]);
                }
                continue;
            } else {
                String key = headingKey(c.after() == null ? "" : c.after());
                int at = titles.indexOf(key);
                if (at < 0) {
                    throw new IllegalStateException(lessonId + ": checks.yml の after に合う ## 見出しがありません: " + c.after());
                }
                if (titles.lastIndexOf(key) != at) {
                    throw new IllegalStateException(lessonId + ": 同じ ## 見出しが 2 つあり、checks.yml の after でどちらか決められません: " + c.after());
                }
                pos = sections.get(at)[1];
            }
            inserts.computeIfAbsent(pos, k -> new StringBuilder()).append(checkHtml(lessonId, i, c));
        }
        closes.forEach((at, text) -> inserts.merge(at, text, (open, close) -> close.append(open)));
        // 消す塊の中に差し込みがあるときは消さない(畳みの閉じなどを巻き込まないように)
        drops.entrySet().removeIf(d -> inserts.keySet().stream().anyMatch(k -> k > d.getKey() && k < d.getValue()));
        // 後ろから、同じ位置では消してから差し込む
        java.util.TreeSet<Integer> at = new java.util.TreeSet<>(inserts.keySet());
        at.addAll(drops.keySet());
        StringBuilder out = new StringBuilder(html);
        for (int p : at.descendingSet()) {
            Integer end = drops.get(p);
            if (end != null) {
                out.delete(p, end);
            }
            StringBuilder text = inserts.get(p);
            if (text != null) {
                out.insert(p, text);
            }
        }
        return out.toString();
    }

    private static final Pattern PRE = Pattern.compile("<pre\\b.*?</pre>", Pattern.DOTALL);

    /** 本文の塊がコード(pre)で、問いの HTML の中に同じコード(空白を除いて同じ文字)があるか。 */
    static boolean sameCode(String html, int[] block, String questionHtml) {
        String b = html.substring(block[0], block[1]);
        if (!b.startsWith("<pre")) {
            return false;
        }
        String key = codeKey(b);
        Matcher m = PRE.matcher(questionHtml);
        while (m.find()) {
            if (!key.isEmpty() && codeKey(m.group()).equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String codeKey(String preHtml) {
        return org.springframework.web.util.HtmlUtils.htmlUnescape(preHtml.replaceAll("<[^>]+>", "")).replaceAll("\\s+", "");
    }

    /** 見出しの比べ方: Markdown の記号(` と **)を外し、前後の空白を落とす。 */
    private static String headingKey(String s) {
        return s.replace("`", "").replace("**", "").strip();
    }

    // コメントは 1 つの印として読み飛ばす(中のタグを数えない)
    private static final Pattern TAG = Pattern.compile("<!--.*?-->|<(/?)([a-zA-Z][a-zA-Z0-9-]*)\\b[^>]*?(/?)>", Pattern.DOTALL);
    private static final java.util.Set<String> VOID_TAGS = java.util.Set.of("br", "img", "hr", "input", "meta", "link", "wbr", "col",
            "source", "area", "base", "embed", "param", "track");
    private static final Pattern HEADING = Pattern.compile("<h[1-6]\\b");

    /** 本文の一番外側の塊(段落・コード・箇条書き・表・図・吹き出し)の [始まり, 終わり]。Markdown から作った整った HTML が前提。 */
    static List<int[]> topBlocks(String html) {
        List<int[]> out = new ArrayList<>();
        int depth = 0;
        int start = -1;
        Matcher m = TAG.matcher(html);
        while (m.find()) {
            if (m.group(2) == null) {
                continue; // コメント
            }
            String name = m.group(2).toLowerCase(java.util.Locale.ROOT);
            if (!m.group(1).isEmpty()) {
                if (depth == 0) {
                    continue; // 対になる開きタグの無い閉じタグは無視する(後ろの塊を巻き込まないように)
                }
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(new int[] {start, m.end()});
                    start = -1;
                }
            } else if (m.group(3).isEmpty() && !VOID_TAGS.contains(name)) {
                if (depth == 0) {
                    start = m.start();
                }
                depth++;
            }
        }
        return out;
    }

    /** before の置き場所: 書き出しがその文字の塊(1 つだけ)の始まり。見出しと同じく ` と ** を外し、空白をそろえて比べる。 */
    private static int beforePosition(String lessonId, String html, List<int[]> blocks, String before) {
        String key = blockKey(before);
        int found = -1;
        for (int[] b : blocks) {
            if (HEADING.matcher(html).region(b[0], b[1]).lookingAt()) {
                continue; // 見出しの前に置くときは after(前の節の終わり)で書く
            }
            String text = blockKey(org.springframework.web.util.HtmlUtils.htmlUnescape(
                    html.substring(b[0], b[1]).replaceAll("<[^>]+>", " ")));
            if (!key.isEmpty() && text.startsWith(key)) {
                if (found >= 0) {
                    throw new IllegalStateException(lessonId + ": checks.yml の before で始まる塊が 2 つあり、どちらか決められません: " + before);
                }
                found = b[0];
            }
        }
        if (found < 0) {
            throw new IllegalStateException(lessonId + ": checks.yml の before で始まる塊が本文にありません: " + before);
        }
        return found;
    }

    private static String blockKey(String s) {
        return headingKey(s.replaceAll("\\s+", " ")).replace(" ", "");
    }

    private static String checkHtml(String lessonId, int index, Lesson.Check c) {
        List<Integer> order = new ArrayList<>(java.util.stream.IntStream.range(0, c.choicesHtml().size()).boxed().toList());
        Collections.shuffle(order, new java.util.Random((lessonId + "!" + index).hashCode()));
        String id = "check-" + index;
        StringBuilder b = new StringBuilder();
        b.append("<aside class=\"check\" data-check=\"").append(index).append("\" data-answer=\"").append(c.answer())
                .append("\" aria-labelledby=\"").append(id).append("\">\n<p class=\"check-label\" id=\"").append(id)
                .append("\">手を止めて</p>\n<div class=\"check-q\" id=\"").append(id).append("-q\">").append(c.questionHtml())
                .append("</div>\n<div class=\"check-choices\" role=\"group\" aria-labelledby=\"").append(id).append("-q\">");
        for (int j : order) {
            b.append("<button type=\"button\" class=\"choice\" data-c=\"").append(j)
                    .append("\"><span class=\"choice-dot\" aria-hidden=\"true\"></span><span class=\"choice-text\">")
                    .append(c.choicesHtml().get(j)).append("</span></button>");
        }
        // 理由へはフォーカスを移して読ませる(aria-live も付けると二重に読まれる)
        b.append("</div>\n<div class=\"check-explain\" tabindex=\"-1\" hidden>");
        for (int j = 0; j < c.whyHtml().size(); j++) {
            if (!c.whyHtml().get(j).isEmpty()) {
                b.append("<p class=\"check-why\" data-for=\"").append(j).append("\" hidden>").append(c.whyHtml().get(j)).append("</p>");
            }
        }
        // 理由は包んでおく(答えの前に置いた問いでは、本文の続きが種明かしをするので画面で隠す。復習では出す)
        b.append("<div class=\"check-ex-body\">").append(c.explanationHtml()).append("</div></div>\n</aside>\n");
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
                    strList(r, "trace").stream().map(markdown::renderInline).toList(),
                    whyHtml(r, choices.size(), answer, lessonId + " の読解: " + str(r, "title"))));
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
