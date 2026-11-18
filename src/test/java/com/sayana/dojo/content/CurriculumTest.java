package com.sayana.dojo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.sayana.dojo.config.DojoProperties;
import com.sayana.dojo.exec.ExecutionGate;
import com.sayana.dojo.exec.GradeResult;
import com.sayana.dojo.exec.Grader;
import com.sayana.dojo.exec.JShellRunner;
import com.sayana.dojo.exec.RunResult;
import com.sayana.dojo.mobile.SampleOutputs;

/**
 * 教材そのもののテスト。
 * <ul>
 *   <li>全演習の模範解答が採点テストに合格する</li>
 *   <li>全演習の雛形はコンパイルでき、採点テストには落ちる(テストが甘くない)</li>
 *   <li>全サンプルが JShell で最後まで動き、スマホ版に載せる実行結果の控えと同じ出力になる</li>
 *   <li>公開レッスンに必要な要素(解説・サンプル・演習・クイズ5問・コラム)がそろっている</li>
 * </ul>
 */
class CurriculumTest {

    private static final DojoProperties PROPS = new DojoProperties();
    private static final ContentRepository CONTENT = new ContentRepository(PROPS, new MarkdownRenderer());
    private static final Grader GRADER = new Grader(PROPS, new ExecutionGate(PROPS));
    private static final JShellRunner RUNNER = new JShellRunner(PROPS, new ExecutionGate(PROPS));
    private static final SampleOutputs.Data OUTPUTS = loadOutputs();

    private static SampleOutputs.Data loadOutputs() {
        try {
            return SampleOutputs.load(SampleOutputs.FILE);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static Stream<Arguments> exercises() {
        return CONTENT.allExercises().stream().map(e -> Arguments.of(e.key(), e));
    }

    static Stream<Arguments> samples() {
        return CONTENT.readyLessons().stream().flatMap(l -> l.samples().stream()
                .map(s -> Arguments.of(l.id() + " / " + s.title(), s)));
    }

    @Test
    @DisplayName("4部構成・全55レッスンがすべて公開されている")
    void curriculumShape() {
        assertThat(CONTENT.parts()).hasSize(4);
        assertThat(CONTENT.allLessons()).hasSize(55);
        assertThat(CONTENT.allLessons()).allSatisfy(l -> assertThat(l.ready()).as(l.id()).isTrue());
    }

    @Test
    @DisplayName("ローカル版から移した読解171問・仕事のコード36件・用語405語がそろっていて、読解の答えは選択肢の中にある")
    void migratedFromLocalDojo() {
        assertThat(CONTENT.readyLessons().stream().mapToInt(l -> l.reading().size()).sum()).isEqualTo(171);
        assertThat(CONTENT.readyLessons().stream().mapToInt(l -> l.atWork().size()).sum()).isEqualTo(36);
        assertThat(CONTENT.glossary()).hasSize(405);
        for (Lesson l : CONTENT.readyLessons()) {
            l.reading().forEach(r -> {
                assertThat(r.code()).as(l.id() + " の読解のコード").isNotBlank();
                assertThat(r.answer()).as(l.id() + " / " + r.title()).isBetween(0, r.choicesHtml().size() - 1);
            });
            l.atWork().forEach(a -> a.notesHtml().keySet().forEach(n -> assertThat(n)
                    .as(l.id() + " の仕事のコードの行番号").isBetween(1, (int) a.code().lines().count())));
        }
    }

    @Test
    @DisplayName("公開レッスンには解説・クイズ5問・コラムがそろっていて、演習かSTS手順がある")
    void readyLessonsAreComplete() {
        for (Lesson l : CONTENT.readyLessons()) {
            assertThat(l.bodyMarkdown()).as(l.id() + " の解説").hasSizeGreaterThan(2000);
            assertThat(l.quiz()).as(l.id() + " のクイズ").hasSize(5);
            assertThat(l.columns()).as(l.id() + " のコラム").isNotEmpty();
            l.quiz().forEach(q -> assertThat(q.choices()).as(l.id() + " のクイズの選択肢").hasSizeGreaterThanOrEqualTo(2));
            List<String> keys = l.checklist().stream().map(Lesson.ChecklistItem::key).toList();
            assertThat(keys).as(l.id() + " のチェックリストのキー").doesNotHaveDuplicates();
            if (l.hasSts()) {
                // 画面上で実行できない内容が中心のレッスンは、手順とチェックリストが主役
                assertThat(l.sts().steps()).as(l.id() + " のSTS手順").isNotEmpty();
                assertThat(l.checklist()).as(l.id() + " のチェックリスト").isNotEmpty();
                assertThat(l.exercises()).as(l.id() + " の演習").hasSizeLessThanOrEqualTo(3);
            } else {
                assertThat(l.samples()).as(l.id() + " のサンプル").isNotEmpty();
                assertThat(l.exercises()).as(l.id() + " の演習").hasSizeBetween(1, 3);
            }
        }
        assertThat(CONTENT.lesson("java-hello").orElseThrow().hasSts()).as("STSでやってみよう").isTrue();
    }

    @Test
    @DisplayName("誤答の 1 文(why)は解説と同じ話をくり返さず、地の文の文字列は ASCII の引用符で書かず、サンプルには予想の問いがある")
    void proseRules() {
        List<String> problems = new java.util.ArrayList<>();
        for (Lesson l : CONTENT.readyLessons()) {
            for (int i = 0; i < l.quiz().size(); i++) {
                var q = l.quiz().get(i);
                proseProblems(l.id() + " quiz " + i, q.questionHtml(), q.choicesHtml(), q.whyHtml(), q.explanationHtml(), problems);
            }
            for (int i = 0; i < l.reading().size(); i++) {
                var r = l.reading().get(i);
                proseProblems(l.id() + " reading " + i, r.questionHtml(), r.choicesHtml(), r.whyHtml(), r.explanationHtml(), problems);
                for (String t : r.traceHtml()) {
                    java.util.regex.Matcher m = ASCII_QUOTED.matcher(plain(t, true));
                    if (m.find()) {
                        problems.add(l.id() + " reading " + i + " trace: 地の文に ASCII の引用符 " + m.group());
                    }
                }
            }
            // 正解がいちばん長い選択肢になる問は、レッスンの 5 問のうち 2 問まで(長さで当てられないように)
            long longest = l.quiz().stream().filter(q -> {
                int a = plain(q.choicesHtml().get(q.answer()), false).replaceAll("\\s+", "").length();
                return java.util.stream.IntStream.range(0, q.choicesHtml().size()).filter(j -> j != q.answer())
                        .allMatch(j -> plain(q.choicesHtml().get(j), false).replaceAll("\\s+", "").length() < a);
            }).count();
            if (longest >= 3) {
                problems.add(l.id() + ": 正解がいちばん長い選択肢の問が " + longest + " 問(5 問のうち 2 問まで)");
            }
            for (int i = 0; i < l.checks().size(); i++) {
                var c = l.checks().get(i);
                proseProblems(l.id() + " check " + i, c.questionHtml(), c.choicesHtml(), c.whyHtml(), c.explanationHtml(), problems);
            }
            for (Lesson.Sample s : l.samples()) {
                // スマホ版の predictLine と同じ拾い方(? / だろうか / でしょうか で終わる文)
                String text = plain(s.descriptionHtml(), false).replaceAll("\\s+", " ").strip();
                boolean predict = java.util.Arrays.stream(text.split("(?<=[。?？])")).map(String::strip)
                        .anyMatch(x -> x.matches(".*([?？]|だろうか。?|でしょうか。?)$"));
                if (!predict) {
                    problems.add(l.id() + " サンプル「" + s.title() + "」の説明に、予想の問い(? で終わる文)が無い");
                }
            }
        }
        assertThat(problems).as("CONTENT_FORMAT.md の why・引用符・予想の問いの決まり").isEmpty();
    }

    private static final java.util.regex.Pattern ASCII_QUOTED = java.util.regex.Pattern.compile("\"[^\"\\n]{1,60}\"");
    private static final java.util.regex.Pattern BARE_CODE = java.util.regex.Pattern.compile(
            "[A-Za-z_]\\w*\\([^()\\s]{0,20}\\)|\\+\\+|--(?=\\w)|\\+=|-=|==|!=|->|::|&&|\\|\\||\\b[A-Z][A-Za-z]*(?:Exception|Error)\\b");

    private static void proseProblems(String where, String question, List<String> choices, List<String> why, String explanation,
                                      List<String> problems) {
        List<String> texts = new java.util.ArrayList<>(List.of(question, explanation));
        texts.addAll(choices);
        texts.addAll(why);
        for (String t : texts) {
            java.util.regex.Matcher m = ASCII_QUOTED.matcher(plain(t, true));
            if (m.find()) {
                problems.add(where + ": 地の文に ASCII の引用符 " + m.group() + "(出力はコードに、リテラルは引用符ごとコードに)");
            }
        }
        // why と選択肢のコードの字句はコード書式に(地の文の書体だと「+ =」と割れ、例外名は語の途中で折れる)
        List<String> codeChecked = new java.util.ArrayList<>(choices);
        codeChecked.addAll(why);
        for (String t : codeChecked) {
            java.util.regex.Matcher m = BARE_CODE.matcher(plain(t, true));
            if (m.find()) {
                problems.add(where + ": コードの字句「" + m.group() + "」がコード書式の外にある");
            }
        }
        String ex = plain(explanation, true).replaceAll("\\s+", "");
        for (int j = 0; j < why.size(); j++) {
            String w = plain(why.get(j), true).replaceAll("\\s+", "");
            String same = longestCommon(w, ex);
            if (same.length() >= 12) {
                problems.add(where + " why" + j + ": 解説と同じ並び「" + same + "」(why は読み違え、解説は正解の筋)");
            }
        }
    }

    /** HTML から地の文を取り出す。コード(pre と code)は 1 字の印に置き換える(dropCode)か、文字だけ残す。 */
    private static String plain(String html, boolean dropCode) {
        String s = html == null ? "" : html;
        if (dropCode) {
            s = s.replaceAll("(?s)<pre\\b.*?</pre>", "〓").replaceAll("(?s)<code\\b.*?</code>", "〓");
        }
        return org.springframework.web.util.HtmlUtils.htmlUnescape(s.replaceAll("<[^>]+>", ""));
    }

    /** 最長の共通部分文字列(印〓を含まない)。 */
    private static String longestCommon(String a, String b) {
        int best = 0;
        int end = 0;
        int[] prev = new int[b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            int[] cur = new int[b.length() + 1];
            if (a.charAt(i - 1) != '〓') {
                for (int j = 1; j <= b.length(); j++) {
                    if (a.charAt(i - 1) == b.charAt(j - 1)) {
                        cur[j] = prev[j - 1] + 1;
                        if (cur[j] > best) {
                            best = cur[j];
                            end = i;
                        }
                    }
                }
            }
            prev = cur;
        }
        return a.substring(end - best, end);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exercises")
    @DisplayName("模範解答は採点テストに全部合格する")
    void solutionPasses(String key, ExerciseDef ex) {
        GradeResult r = GRADER.grade(ex.className(), ex.solution(), ex.test(), ex.libraries(), Duration.ofSeconds(40));
        assertThat(r.status()).as(key + "\n" + r.summaryText() + "\n" + r.output()).isEqualTo(GradeResult.Status.PASSED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exercises")
    @DisplayName("雛形はコンパイルできて、採点テストには落ちる")
    void starterCompilesButFails(String key, ExerciseDef ex) {
        GradeResult r = GRADER.grade(ex.className(), ex.starter(), ex.test(), ex.libraries(), Duration.ofSeconds(40));
        assertThat(r.status()).as(key + "\n" + r.summaryText()).isNotIn(GradeResult.Status.COMPILE_ERROR,
                GradeResult.Status.PASSED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    @DisplayName("サンプルは JShell でコンパイルエラーなく最後まで動く")
    void sampleRuns(String name, Lesson.Sample sample) {
        RunResult r = RUNNER.run(sample.code(), Duration.ofSeconds(30), sample.libraries());
        assertThat(r.status()).as(name + "\n" + r.errorText() + "\n" + r.output())
                .isIn(RunResult.Status.OK, RunResult.Status.EXCEPTION);
        assertThat(r.output() + String.join("", r.values())).as(name + " の出力").isNotBlank();
        // スマホ版に載せる実行結果の控えが、本当の出力と同じこと(控えを作ったのと同じ JDK のときだけ比べる)
        if (OUTPUTS.jdk() == Runtime.version().feature()) {
            SampleOutputs.Entry saved = OUTPUTS.entries().get(SampleOutputs.key(sample));
            if (saved != null && !saved.varies()) {
                assertThat(saved.output()).as(name + " の控えの出力(違ったら ./mvnw -q compile exec:java で作り直す)")
                        .isEqualTo(SampleOutputs.lf(r.output()));
                assertThat(saved.values()).as(name + " の控えの式の値")
                        .isEqualTo(r.values().stream().map(SampleOutputs::lf).toList());
                assertThat(saved.exception()).as(name + " の控えの例外").isEqualTo(SampleOutputs.lf(r.exception()));
            }
        }
    }
}
