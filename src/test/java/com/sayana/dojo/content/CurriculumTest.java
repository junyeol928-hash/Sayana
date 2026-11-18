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
        // 読解の選択肢の長さの偏り(全体): [ただ 1 つのいちばん長い選択肢がある問, そのうち正解, ただ 1 つのいちばん短い選択肢がある問, そのうち正解]
        int[] readingBias = new int[4];
        for (Lesson l : CONTENT.readyLessons()) {
            for (int i = 0; i < l.quiz().size(); i++) {
                var q = l.quiz().get(i);
                proseProblems(l.id() + " quiz " + i, q.questionHtml(), q.choicesHtml(), q.whyHtml(), q.explanationHtml(), List.of(), problems);
            }
            int readingLongest = 0;
            for (int i = 0; i < l.reading().size(); i++) {
                var r = l.reading().get(i);
                proseProblems(l.id() + " reading " + i, r.questionHtml(), r.choicesHtml(), r.whyHtml(), r.explanationHtml(), r.traceHtml(), problems);
                for (String t : r.traceHtml()) {
                    String p = plain(t, true);
                    java.util.regex.Matcher m = ASCII_QUOTED.matcher(p);
                    if (m.find()) {
                        problems.add(l.id() + " reading " + i + " trace: 地の文に ASCII の引用符 " + m.group());
                    }
                    String code = bareCode(p);
                    if (code != null) {
                        problems.add(l.id() + " reading " + i + " trace: コードの字句「" + code + "」がコード書式の外にある");
                    }
                }
                int[] len = r.choicesHtml().stream().mapToInt(CurriculumTest::choiceLength).toArray();
                int max = java.util.Arrays.stream(len).max().orElse(0);
                int min = java.util.Arrays.stream(len).min().orElse(0);
                if (java.util.Arrays.stream(len).filter(x -> x == max).count() == 1) {
                    readingBias[0]++;
                    if (len[r.answer()] == max) {
                        readingBias[1]++;
                        readingLongest++;
                    }
                }
                if (java.util.Arrays.stream(len).filter(x -> x == min).count() == 1) {
                    readingBias[2]++;
                    if (len[r.answer()] == min) {
                        readingBias[3]++;
                    }
                }
            }
            if (readingLongest >= 3) {
                problems.add(l.id() + ": 読解で正解がいちばん長い選択肢の問が " + readingLongest + " 問(レッスンごとに 2 問まで)");
            }
            // 正解がいちばん長い選択肢になる問は、レッスンの 5 問のうち 2 問まで(長さで当てられないように)
            long longest = l.quiz().stream().filter(q -> {
                int a = choiceLength(q.choicesHtml().get(q.answer()));
                return java.util.stream.IntStream.range(0, q.choicesHtml().size()).filter(j -> j != q.answer())
                        .allMatch(j -> choiceLength(q.choicesHtml().get(j)) < a);
            }).count();
            if (longest >= 3) {
                problems.add(l.id() + ": 正解がいちばん長い選択肢の問が " + longest + " 問(5 問のうち 2 問まで)");
            }
            for (int i = 0; i < l.checks().size(); i++) {
                var c = l.checks().get(i);
                proseProblems(l.id() + " check " + i, c.questionHtml(), c.choicesHtml(), c.whyHtml(), c.explanationHtml(), List.of(), problems);
            }
            for (Lesson.Sample s : l.samples()) {
                // スマホ版の predictLine と同じ拾い方(? / だろうか / でしょうか で終わる文)
                // コードの中の ?(`? extends Number`)は文の終わりではない(スマホ版の predictLine と同じ)
                String text = plain(java.util.regex.Pattern.compile("(?s)<code>(.*?)</code>").matcher(s.descriptionHtml())
                        .replaceAll(mr -> java.util.regex.Matcher.quoteReplacement("<code>" + mr.group(1).replace("?", "\uE000") + "</code>")), false)
                        .replaceAll("\\s+", " ").strip();
                boolean predict = java.util.Arrays.stream(text.split("(?<=[。?？])")).map(String::strip)
                        .anyMatch(x -> x.matches(".*([?？]|だろうか。?|でしょうか。?)$"));
                if (!predict) {
                    problems.add(l.id() + " サンプル「" + s.title() + "」の説明に、予想の問い(? で終わる文)が無い");
                }
                // 予想の問いは説明の最後の文に(スマホは最後の問いを「予想: 〜」に出し、説明の最後の文なら説明から外す。
                // 問いの後ろに文が続くと、同じ問いが 2 回出た)
                List<String> sentences = java.util.Arrays.stream(text.split("(?<=[。?？])")).map(String::strip).filter(x -> !x.isEmpty()).toList();
                if (predict && !sentences.isEmpty() && !sentences.getLast().matches(".*([?？]|だろうか。?|でしょうか。?)$")) {
                    problems.add(l.id() + " サンプル「" + s.title() + "」: 予想の問いを説明の最後の文に(後ろに「" + sentences.getLast() + "」)");
                }
                java.util.Set<String> sampleCode = new java.util.HashSet<>();
                java.util.regex.Matcher sc = INLINE_CODE.matcher(s.descriptionHtml());
                while (sc.find()) {
                    sampleCode.add(org.springframework.web.util.HtmlUtils.htmlUnescape(sc.group(1)).strip());
                }
                java.util.regex.Matcher sw = ASCII_WORD.matcher(plain(s.descriptionHtml(), true));
                while (sw.find()) {
                    String word = sw.group().replaceAll("[.\\-]+$", "");
                    if (word.length() >= 2 && sampleCode.contains(word)) {
                        problems.add(l.id() + " サンプル「" + s.title() + "」: 「" + word + "」は同じ説明でコード書式なので、地の文でもコード書式に");
                        break;
                    }
                }
                // 「予想してから見よう」は、閉じた欄の見出し「予想したら、実行結果を見る」が言う(説明で重ねない)
                java.util.Arrays.stream(text.split("(?<=[。?？])")).map(String::strip)
                        .filter(x -> x.contains("予想して") && !x.matches(".*([?？]|だろうか。?|でしょうか。?)$"))
                        .forEach(x -> problems.add(l.id() + " サンプル「" + s.title() + "」: 予想を促す文「" + x + "」は欄の見出しが言う"));
            }
        }
        // 読解はいちばん長いのを選ぶ・いちばん短いのを捨てる、で当てられないように(当てずっぽうなら 4 択で 25%)
        if (readingBias[1] * 100 > readingBias[0] * 30) {
            problems.add("読解: 正解がいちばん長い選択肢の問が " + readingBias[1] + " / " + readingBias[0] + "(30% まで)");
        }
        if (readingBias[3] * 100 < readingBias[2] * 15) {
            problems.add("読解: 正解がいちばん短い選択肢の問が " + readingBias[3] + " / " + readingBias[2] + "(15% 以上)");
        }
        for (Term t : CONTENT.glossary()) {
            java.util.regex.Matcher g = GLOSSARY_CODE.matcher(plain(t.defHtml(), true));
            String code = g.find() ? g.group() : bareCode(plain(t.defHtml(), true));
            if (code != null) {
                problems.add("用語集「" + t.term() + "」: コードの字句「" + code + "」がコード書式の外にある");
            }
        }
        assertThat(problems).as("CONTENT_FORMAT.md の why・引用符・予想の問いの決まり").isEmpty();
    }

    private static int choiceLength(String html) {
        return plain(html, false).replaceAll("\\s+", "").length();
    }

    /** 地の文に残ったコードの字句(呼び出し・演算子・例外名・比較・代入)。無ければ null。 */
    private static String bareCode(String plainText) {
        java.util.regex.Matcher m = BARE_CODE.matcher(plainText);
        if (m.find()) {
            return m.group();
        }
        m = BARE_COMPARE.matcher(plainText);
        return m.find() ? m.group() : null;
    }

    private static final java.util.regex.Pattern ASCII_QUOTED = java.util.regex.Pattern.compile("\"[^\"\\n]{1,60}\"");
    private static final java.util.regex.Pattern BARE_CODE = java.util.regex.Pattern.compile(
            // 括弧の中は ASCII だけ(「byte(8ビットの数)」「case(〓)」のような地の文の補足は拾わない)
            "[A-Za-z_]\\w*\\([!-'*-~]{0,20}\\)|\\+\\+|--(?=\\w)|\\+=|-=|==|!=|->|::|&&|\\|\\||\\b[A-Z][A-Za-z]*(?:Exception|Error)\\b");
    /** 比較と代入(x=10・10 < 5・T = Integer)。ASCII の語どうしを結ぶものだけ */
    private static final java.util.regex.Pattern BARE_COMPARE = java.util.regex.Pattern.compile(
            // メニューの道筋(Maven > Update Project)は比較ではない
            "(?<![A-Za-z0-9_])(?![A-Z][a-z]+ > [A-Z])[A-Za-z0-9_]+ ?(?:<=|>=|<|>|=) ?[A-Za-z0-9_]+(?![A-Za-z0-9_])");
    private static final java.util.regex.Pattern INLINE_CODE = java.util.regex.Pattern.compile("<code>([^<]{2,40})</code>");
    /** 用語集の説明で、コードにする語(完全修飾名 java.util.ArrayList と、リテラル true / false / null) */
    private static final java.util.regex.Pattern GLOSSARY_CODE = java.util.regex.Pattern.compile(
            "(?<![A-Za-z0-9_.])[a-z]\\w*(?:\\.[a-z]\\w*)+\\.[A-Z]\\w*|(?<![A-Za-z0-9_])(?:true|false|null)(?![A-Za-z0-9_])");
    private static final java.util.regex.Pattern ASCII_WORD = java.util.regex.Pattern.compile(
            "(?<![A-Za-z0-9_@.])[A-Za-z_@][A-Za-z0-9_.@\\-]*");

    private static void proseProblems(String where, String question, List<String> choices, List<String> why, String explanation,
                                      List<String> trace, List<String> problems) {
        List<String> texts = new java.util.ArrayList<>(List.of(question, explanation));
        texts.addAll(choices);
        texts.addAll(why);
        for (String t : texts) {
            java.util.regex.Matcher m = ASCII_QUOTED.matcher(plain(t, true));
            if (m.find()) {
                problems.add(where + ": 地の文に ASCII の引用符 " + m.group() + "(出力はコードに、リテラルは引用符ごとコードに)");
            }
        }
        // why と選択肢のコードの字句はコード書式に(地の文の書体だと「+ =」と割れ、例外名は語の途中で折れる)。
        // 比較と代入(x=10・10<5)は解説でも
        List<String> codeChecked = new java.util.ArrayList<>(choices);
        codeChecked.addAll(why);
        for (String t : codeChecked) {
            String code = bareCode(plain(t, true));
            if (code != null) {
                problems.add(where + ": コードの字句「" + code + "」がコード書式の外にある");
            }
        }
        java.util.regex.Matcher cmp = BARE_COMPARE.matcher(plain(explanation, true));
        if (cmp.find()) {
            problems.add(where + " 解説: コードの字句「" + cmp.group() + "」がコード書式の外にある");
        }
        // 解説の長いメッセージ(24 字以上で空白を含むコード)は独立した 1 行のブロックになるので、括弧の中に置かない
        // (「(」が行末に残り、文が括弧ごと 3 つに割れる)
        // 長さは画面と同じく文字で数える(&quot; などの書き方で数えると、`"Java" == "Java"` まで長いことになった)
        String marked = plain(java.util.regex.Pattern.compile("<code>([^<]*)</code>").matcher(explanation == null ? "" : explanation)
                .replaceAll(mr -> {
                    String text = org.springframework.web.util.HtmlUtils.htmlUnescape(mr.group(1)).strip();
                    return text.length() >= 24 && text.matches("(?s).*\\s.*") ? "◆" : java.util.regex.Matcher.quoteReplacement(mr.group());
                }), true);
        if (java.util.regex.Pattern.compile("[(（][^)）]*◆").matcher(marked).find()) {
            problems.add(where + " 解説: 長いメッセージのコードが括弧の中にある(括弧を外し「次のメッセージ:」のように前置きする)");
        }
        // 同じ問いでコード書式にした識別子は、why でもコード書式に(終わりの画面で why の 1 文目が主役になる)
        java.util.Set<String> codeWords = new java.util.HashSet<>();
        List<String> all = new java.util.ArrayList<>(List.of(question, explanation));
        all.addAll(choices);
        all.addAll(why);
        all.addAll(trace);
        for (String t : all) {
            java.util.regex.Matcher c = INLINE_CODE.matcher(t == null ? "" : t);
            while (c.find()) {
                codeWords.add(org.springframework.web.util.HtmlUtils.htmlUnescape(c.group(1)).strip());
            }
        }
        for (int j = 0; j < why.size(); j++) {
            java.util.regex.Matcher w = ASCII_WORD.matcher(plain(why.get(j), true));
            while (w.find()) {
                String word = w.group().replaceAll("[.\\-]+$", "");
                if (word.length() >= 2 && codeWords.contains(word)) {
                    problems.add(where + " why" + j + ": 「" + word + "」は同じ問いでコード書式なので、why でもコード書式に");
                    break;
                }
            }
        }
        // 逆向きも: 問い・選択肢・why・追跡・解説のどこかでコード書式にした語は、解説の地の文でもコード書式に
        // (答えた直後の 1 つの箱で、同じ switch がチップと地の文の 2 通りに出ていた。24 回目の審査)
        java.util.regex.Matcher ew = ASCII_WORD.matcher(plain(explanation, true));
        while (ew.find()) {
            String word = ew.group().replaceAll("[.\\-]+$", "");
            if (word.length() >= 2 && codeWords.contains(word)) {
                problems.add(where + " 解説: 「" + word + "」は同じ問いでコード書式なので、解説でもコード書式に");
                break;
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
