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
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.sayana.dojo.config.DojoProperties;
import com.sayana.dojo.content.Lesson.ChecklistItem;
import com.sayana.dojo.content.Lesson.Column;
import com.sayana.dojo.content.Lesson.QuizQuestion;
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
                            Map<String, ExerciseDef> exercises) {
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
        this.snapshot = new Snapshot(List.copyOf(parts), Collections.unmodifiableMap(lessons), ordered,
                Collections.unmodifiableMap(exercises));
        log.info("教材を読み込みました: {}部 / {}レッスン(公開 {}) / 演習 {}問", parts.size(), ordered.size(),
                ordered.stream().filter(Lesson::ready).count(), exercises.size());
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
                    List.of(), List.of());
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
        return new Lesson(id, partId, chapterId, str(y, "title"), status, intVal(y, "minutes", 15),
                str(y, "summary"), strList(y, "goals"), body, markdown.render(body), samples, List.copyOf(quiz),
                columns, sts, checklist, List.copyOf(exercises));
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
    private static List<Map<String, Object>> maps(Map<String, Object> m, String key) {
        if (m.get(key) instanceof List<?> l) {
            return l.stream().filter(Map.class::isInstance).map(x -> (Map<String, Object>) x).toList();
        }
        return List.of();
    }
}
