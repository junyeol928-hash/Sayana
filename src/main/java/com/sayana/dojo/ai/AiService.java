package com.sayana.dojo.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.sayana.dojo.ai.LlmClient.LlmRequest;
import com.sayana.dojo.ai.LlmClient.SystemBlock;
import com.sayana.dojo.ai.LlmClient.Turn;
import com.sayana.dojo.config.DojoProperties;
import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.ExerciseDef;
import com.sayana.dojo.content.Lesson;
import com.sayana.dojo.exec.GradeResult;
import com.sayana.dojo.exec.Grader;
import com.sayana.dojo.progress.ChatMessage;
import com.sayana.dojo.progress.ChatMessageRepository;
import com.sayana.dojo.progress.ExerciseCatalog;
import com.sayana.dojo.progress.GeneratedExercise;
import com.sayana.dojo.progress.GeneratedExerciseRepository;
import com.sayana.dojo.progress.HintRecord;
import com.sayana.dojo.progress.HintRecordRepository;
import com.sayana.dojo.progress.LearningService;
import com.sayana.dojo.progress.Submission;
import com.sayana.dojo.progress.SubmissionRepository;

/**
 * Claude API を使う機能をここに集約する(チューター・段階ヒント・レビュー・エラー翻訳・類題生成)。
 * <p>
 * プロンプトの組み立て方:
 * <pre>
 * system[0] = 共通の前置き + レッスン教材   ← cache_control(レッスンごとに同じバイト列なのでキャッシュが効く)
 * system[1] = 機能ごとの指示(prompts/*.md)
 * messages  = 会話・学習者のコード・エラーなど(毎回変わるもの)
 * </pre>
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    public static final int MAX_HINT_LEVEL = 3;

    private final LlmClient llm;
    private final PromptLibrary prompts;
    private final ContentRepository content;
    private final ExerciseCatalog exercises;
    private final ChatMessageRepository chat;
    private final HintRecordRepository hints;
    private final SubmissionRepository submissions;
    private final GeneratedExerciseRepository generated;
    private final LearningService learning;
    private final Grader grader;
    private final DojoProperties.Ai config;
    private final Clock clock;
    private final Map<String, String> contextCache = new ConcurrentHashMap<>();

    public AiService(LlmClient llm, PromptLibrary prompts, ContentRepository content, ExerciseCatalog exercises,
                     ChatMessageRepository chat, HintRecordRepository hints, SubmissionRepository submissions,
                     GeneratedExerciseRepository generated, LearningService learning, Grader grader,
                     DojoProperties props, Clock clock) {
        this.llm = llm;
        this.prompts = prompts;
        this.content = content;
        this.exercises = exercises;
        this.chat = chat;
        this.hints = hints;
        this.submissions = submissions;
        this.generated = generated;
        this.learning = learning;
        this.grader = grader;
        this.config = props.getAi();
        this.clock = clock;
    }

    public boolean available() {
        return llm.isAvailable();
    }

    // ------------------------------------------------------------------ チューター

    public List<ChatMessage> chatHistory(String lessonId) {
        return chat.findByLessonIdOrderByIdAsc(lessonId);
    }

    /** チューターに質問する。成功したら質問と回答を保存して返す。 */
    public List<ChatMessage> askTutor(String lessonId, String question, String code) {
        Lesson lesson = lesson(lessonId);
        List<ChatMessage> recent = new ArrayList<>(
                chat.findByLessonIdOrderByIdDesc(lessonId, PageRequest.of(0, config.getChatHistoryLimit())));
        Collections.reverse(recent);
        // 会話は user から始まる必要がある
        while (!recent.isEmpty() && !recent.get(0).isUser()) {
            recent.remove(0);
        }
        List<Turn> turns = new ArrayList<>();
        recent.forEach(m -> turns.add(new Turn(m.getRole(), m.getContent())));
        StringBuilder msg = new StringBuilder();
        if (code != null && !code.isBlank()) {
            msg.append("【いまエディタにある私のコード】\n```java\n").append(code.strip()).append("\n```\n\n");
        }
        msg.append(question.strip());
        turns.add(Turn.user(msg.toString()));

        String answer = call(config.getModels().getTutor(), "low", lesson, "tutor", turns);
        Instant now = Instant.now(clock);
        ChatMessage q = chat.save(new ChatMessage(lessonId, "user", question.strip(), now));
        ChatMessage a = chat.save(new ChatMessage(lessonId, "assistant", answer, now.plusMillis(1)));
        return List.of(q, a);
    }

    public void clearChat(String lessonId) {
        chat.deleteAll(chat.findByLessonIdOrderByIdAsc(lessonId));
    }

    // ------------------------------------------------------------------ 段階ヒント

    public List<HintRecord> hintsFor(String exerciseKey) {
        return hints.findByExerciseKeyOrderByLevelAscIdAsc(exerciseKey);
    }

    /** 次の段階のヒントを出す(方向性 → 手がかり → ほぼ答え)。 */
    public HintRecord nextHint(String exerciseKey, String code) {
        ExerciseDef ex = exercises.find(exerciseKey).orElseThrow(() -> new AiException("演習が見つかりません"));
        List<HintRecord> previous = hintsFor(exerciseKey);
        int level = previous.size() + 1;
        if (level > MAX_HINT_LEVEL) {
            throw new AiException("ヒントは3段階で全部です。模範解答を見るか、AIチューターに質問してみましょう。");
        }
        StringBuilder msg = new StringBuilder();
        msg.append("【演習】").append(ex.title()).append("(クラス名: ").append(ex.className()).append(")\n")
                .append(ex.problemMarkdown().strip()).append("\n\n");
        msg.append("【私のいまのコード】\n```java\n").append(blankToStarter(code, ex)).append("\n```\n\n");
        if (!previous.isEmpty()) {
            msg.append("【これまでに出たヒント】\n");
            previous.forEach(h -> msg.append("- レベル").append(h.getLevel()).append(": ")
                    .append(h.getText().replace("\n", " ")).append('\n'));
            msg.append('\n');
        }
        msg.append("レベル").append(level).append("のヒントをください。");
        String text = call(config.getModels().getHint(), "low", lesson(ex.lessonId()), "hint",
                List.of(Turn.user(msg.toString())));
        return hints.save(new HintRecord(exerciseKey, level, text, Instant.now(clock)));
    }

    public void resetHints(String exerciseKey) {
        hints.deleteAll(hintsFor(exerciseKey));
    }

    // ------------------------------------------------------------------ コードレビュー

    /** 提出に対する講評を作って保存する。 */
    public Submission review(long submissionId) {
        Submission s = submissions.findById(submissionId).orElseThrow(() -> new AiException("提出が見つかりません"));
        ExerciseDef ex = exercises.find(s.getExerciseKey()).orElseThrow(() -> new AiException("演習が見つかりません"));
        GradeResult result = learning.readResult(s);
        String msg = """
                【演習】%s(クラス名: %s)
                %s

                【私の提出コード】
                ```java
                %s
                ```

                【自動採点の結果】
                %s

                【模範解答(参考。学習者にはまだ見せていない可能性があるので、丸写しを促さないこと)】
                ```java
                %s
                ```

                講評をお願いします。
                """.formatted(ex.title(), ex.className(), ex.problemMarkdown().strip(), s.getCode().strip(),
                result.summaryText(), ex.solution().strip());
        String text = call(config.getModels().getReview(), "medium", lesson(ex.lessonId()), "review",
                List.of(Turn.user(msg)));
        s.setAiReview(text);
        return submissions.save(s);
    }

    // ------------------------------------------------------------------ エラー翻訳

    /** 提出のエラー(コンパイルエラー・テスト失敗など)を解説して保存する。 */
    public Submission explainSubmission(long submissionId) {
        Submission s = submissions.findById(submissionId).orElseThrow(() -> new AiException("提出が見つかりません"));
        ExerciseDef ex = exercises.find(s.getExerciseKey()).orElseThrow(() -> new AiException("演習が見つかりません"));
        GradeResult result = learning.readResult(s);
        String text = explain(ex.lessonId(), s.getCode(), result.summaryText(),
                "演習「" + ex.title() + "」の提出です。問題文:\n" + ex.problemMarkdown().strip());
        s.setErrorExplanation(text);
        return submissions.save(s);
    }

    /** サンプル実行などのエラーを解説する(保存しない)。lessonId は null 可。 */
    public String explain(String lessonId, String code, String errorText, String situation) {
        String msg = """
                %s

                【コード】
                ```java
                %s
                ```

                【エラー】
                ```text
                %s
                ```

                このエラーを解説してください。
                """.formatted(situation == null ? "サンプルコードを実行したらエラーになりました。" : situation,
                code == null ? "" : code.strip(), errorText.strip());
        return call(config.getModels().getExplain(), "low", lessonId == null ? null : lesson(lessonId),
                "explain-error", List.of(Turn.user(msg)));
    }

    // ------------------------------------------------------------------ 類題生成

    private static final Pattern TAG = Pattern.compile("<(title|className|difficulty|problem|starter|solution|test)>(.*?)</\\1>",
            Pattern.DOTALL);

    /** 類題を作り、模範解答が採点テストに合格することを確かめてから保存する。最大2回まで作り直す。 */
    public GeneratedExercise generateExercise(String lessonId) {
        Lesson lesson = lesson(lessonId);
        StringBuilder existing = new StringBuilder("【このレッスンの既存の演習(題材とクラス名を重ねないこと)】\n");
        lesson.exercises().forEach(e -> existing.append("- ").append(e.title()).append(" / ").append(e.className())
                .append('\n'));
        exercises.generatedFor(lessonId).forEach(e -> existing.append("- ").append(e.title()).append(" / ")
                .append(e.className()).append('\n'));
        // 教材の演習が Spring を使うレッスンなら、類題でも Spring を使ってよい
        List<String> libraries = lesson.exercises().stream().flatMap(e -> e.libraries().stream()).distinct().toList();
        String libraryNote = libraries.contains("spring")
                ? "\n採点のクラスパスには Spring Framework(spring-context / spring-webmvc / spring-test の MockMvc / Jackson / Bean Validation)も入っています。必要なら使ってよいですが、Spring Boot の自動設定(@SpringBootTest)は使わず、AnnotationConfigApplicationContext や MockMvcBuilders.standaloneSetup を使ってください。"
                : "";
        List<Turn> turns = new ArrayList<>();
        turns.add(Turn.user(existing + libraryNote + "\nこのレッスンの類題を1問作ってください。"));
        String lastProblem = "";
        for (int attempt = 1; attempt <= 2; attempt++) {
            String raw = call(config.getModels().getGenerate(), "medium", lesson, "generate-exercise", turns);
            Optional<Draft> draft = parse(raw);
            if (draft.isEmpty()) {
                lastProblem = "出力の形式(タグ)が崩れていました。";
            } else {
                Draft d = draft.get();
                lastProblem = validate(d, libraries);
                if (lastProblem == null) {
                    return generated.save(new GeneratedExercise(lessonId, d.title(), d.className(), d.difficulty(),
                            d.problem(), d.starter(), d.solution(), d.test(), config.getModels().getGenerate(),
                            String.join(",", libraries), Instant.now(clock)));
                }
            }
            log.info("類題の検証に失敗(試行{}): {}", attempt, lastProblem);
            turns.add(Turn.assistant(raw));
            turns.add(Turn.user("検証に失敗しました: " + lastProblem + "\n問題を直して、同じタグ形式で全体をもう一度出力してください。"));
        }
        throw new AiException("類題をうまく作れませんでした(" + lastProblem + ")。もう一度試してください。");
    }

    record Draft(String title, String className, int difficulty, String problem, String starter, String solution,
                 String test) {
    }

    static Optional<Draft> parse(String raw) {
        Map<String, String> tags = new java.util.HashMap<>();
        Matcher m = TAG.matcher(raw);
        while (m.find()) {
            tags.put(m.group(1), stripFence(m.group(2)));
        }
        for (String k : List.of("title", "className", "problem", "starter", "solution", "test")) {
            if (tags.getOrDefault(k, "").isBlank()) {
                return Optional.empty();
            }
        }
        String className = tags.get("className").strip();
        if (!className.matches("[A-Z][A-Za-z0-9_]*")) {
            return Optional.empty();
        }
        int difficulty;
        try {
            difficulty = Math.max(1, Math.min(3, Integer.parseInt(tags.getOrDefault("difficulty", "2").strip())));
        } catch (NumberFormatException e) {
            difficulty = 2;
        }
        return Optional.of(new Draft(tags.get("title").strip(), className, difficulty, tags.get("problem").strip(),
                tags.get("starter").strip() + "\n", tags.get("solution").strip() + "\n", tags.get("test").strip() + "\n"));
    }

    private static String stripFence(String s) {
        String t = s.strip();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\n?", "").replaceFirst("\\n?```$", "");
        }
        return t;
    }

    /** 問題なければ null、ダメなら理由。 */
    private String validate(Draft d, List<String> libraries) {
        GradeResult sol = grader.grade(d.className(), d.solution(), d.test(), libraries, Duration.ofSeconds(20));
        if (!sol.passed()) {
            return "模範解答が採点テストに合格しませんでした: " + sol.summaryText();
        }
        GradeResult starter = grader.grade(d.className(), d.starter(), d.test(), libraries, Duration.ofSeconds(20));
        if (starter.status() == GradeResult.Status.COMPILE_ERROR) {
            return "雛形がコンパイルできませんでした: " + starter.summaryText();
        }
        if (starter.passed()) {
            return "雛形のままで採点テストに合格してしまいました(テストが甘い)。";
        }
        return null;
    }

    // ------------------------------------------------------------------ 共通

    private String call(String model, String effort, Lesson lesson, String feature, List<Turn> turns) {
        List<SystemBlock> system = List.of(
                new SystemBlock(context(lesson), true),
                new SystemBlock(prompts.get(feature), false));
        return llm.complete(new LlmRequest(model, effort, system, turns, config.getMaxTokens())).text();
    }

    /** 共通の前置き + レッスン教材。キャッシュが効くよう、同じレッスンなら毎回同じ文字列にする。 */
    String context(Lesson lesson) {
        String key = lesson == null ? "" : lesson.id();
        return contextCache.computeIfAbsent(key, k -> LessonContext.build(prompts.get("common"), lesson));
    }

    /** 教材を読み直したときに呼ぶ。 */
    public void clearContextCache() {
        contextCache.clear();
    }

    private Lesson lesson(String lessonId) {
        return content.lesson(lessonId).orElseThrow(() -> new AiException("レッスンが見つかりません: " + lessonId));
    }

    private static String blankToStarter(String code, ExerciseDef ex) {
        return (code == null || code.isBlank() ? ex.starter() : code).strip();
    }
}
