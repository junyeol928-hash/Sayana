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
        // 手を止めての選択肢の長さの偏り(全体): [ただ 1 つのいちばん短い選択肢がある問, そのうち正解]
        int[] checkBias = new int[2];
        for (Lesson l : CONTENT.readyLessons()) {
            for (int i = 0; i < l.quiz().size(); i++) {
                var q = l.quiz().get(i);
                proseProblems(l.id() + " quiz " + i, q.questionHtml(), q.choicesHtml(), q.whyHtml(), q.explanationHtml(), List.of(), "", problems);
            }
            int readingLongest = 0;
            for (int i = 0; i < l.reading().size(); i++) {
                var r = l.reading().get(i);
                proseProblems(l.id() + " reading " + i, r.questionHtml(), r.choicesHtml(), r.whyHtml(), r.explanationHtml(), r.traceHtml(), r.code(), problems);
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
            // 読解の追跡で、そのコードに出る名前(変数・メソッド・出力の文字)は地の文に書かない(「region は」→「`region` は」)
            for (int i = 0; i < l.reading().size(); i++) {
                var r = l.reading().get(i);
                java.util.Set<String> ids = codeIdentifiers(r.code());
                for (String t : r.traceHtml()) {
                    java.util.regex.Matcher m = IDENT.matcher(plain(t, true));
                    while (m.find()) {
                        // 大文字 1 つの役割の言葉(Bean・Base64)は地の文のまま(仕事のコードの説明と同じ)
                        if (ids.contains(m.group()) && !ENGLISH_WORDS.contains(m.group()) && !m.group().matches("[A-Z][a-z0-9]+")) {
                            problems.add(l.id() + " reading " + i + " trace: コードの名前「" + m.group() + "」がコード書式の外にある");
                            break;
                        }
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
                proseProblems(l.id() + " check " + i, c.questionHtml(), c.choicesHtml(), c.whyHtml(), c.explanationHtml(), List.of(), "", problems);
                // 2 択は、間違えた直後の解き直しが消去法で必ず当たる(仮縫いが思い出せた印にならない)
                if (c.choicesHtml().size() < 3) {
                    problems.add(l.id() + " check " + i + ": 2 択(もっともらしい 3 つ目の誤答と why を足す)");
                }
                int[] len = c.choicesHtml().stream().mapToInt(CurriculumTest::choiceLength).toArray();
                int min = java.util.Arrays.stream(len).min().orElse(0);
                if (java.util.Arrays.stream(len).filter(x -> x == min).count() == 1) {
                    checkBias[0]++;
                    if (len[c.answer()] == min) {
                        checkBias[1]++;
                    }
                }
            }
            // レッスンのコード(サンプル・仕事のコード・本文の Java のブロック)に出る名前と、メソッド名
            String body = CHECK_ASIDE.matcher(l.bodyHtml() == null ? "" : l.bodyHtml()).replaceAll("〓");
            StringBuilder lessonCode = new StringBuilder(javaBlocks(body));
            l.atWork().forEach(aw -> lessonCode.append('\n').append(aw.code()));
            l.samples().forEach(s -> lessonCode.append('\n').append(s.code()));
            java.util.Set<String> allIds = codeIdentifiers(lessonCode.toString());
            java.util.Set<String> methods = methodNames(lessonCode.toString());
            // 仕事のコードの導入・行の説明・要点と、レッスンのゴールでは、コードの語をコード書式に(すぐ上のコードと同じ語が本文の書体で出ていた)
            java.util.Set<String> lessonIds = new java.util.HashSet<>();
            for (Lesson.AtWork aw : l.atWork()) {
                java.util.Set<String> ids = codeIdentifiers(aw.code());
                lessonIds.addAll(ids);
                List<String> notes = new java.util.ArrayList<>();
                notes.add(aw.introHtml());
                notes.addAll(aw.notesHtml().values());
                notes.addAll(aw.pointsHtml());
                for (String t : notes) {
                    String w = bareIdentifier(plain(t, true), ids);
                    if (w == null) {
                        // 大文字 1 つの名前も、レッスンのコードに出るものはコード書式に(「Repository は List を返す」の List。27 回目の審査)
                        w = bareNames(plain(t, true), allIds, java.util.Set.of()).stream().findFirst().orElse(null);
                    }
                    if (w != null) {
                        problems.add(l.id() + " 仕事のコード「" + aw.title() + "」: 識別子「" + w + "」がコード書式の外にある");
                    }
                }
            }
            l.samples().forEach(s -> lessonIds.addAll(codeIdentifiers(s.code())));
            // 本文(図とコードブロックを除く)とサンプルの説明の地の文でも、レッスンのコードに出る名前はコード書式に
            // (「RuntimeException(とその子)と Error」「どちらの Notifier が」が、すぐ上のコードと別の書体だった。27 回目の審査)。
            // 見出しはメソッド名も(「文字列の分岐:equals と null に注意」で、null はチップ、equals は地の文だった)。
            // 枠の見出し(::: warn の後ろ)は Markdown にならない素の文字なので除く
            String bodyProse = FIGURE.matcher(body).replaceAll("〓").replaceAll("(?s)<div class=\"callout-title\">.*?</div>", "〓");
            java.util.regex.Matcher hm = HEADING_H23.matcher(bodyProse);
            while (hm.find()) {
                for (String w : bareNames(plain(hm.group(2), true), allIds, methods)) {
                    problems.add(l.id() + " 見出し「" + plain(hm.group(2), false).strip() + "」: 識別子「" + w + "」がコード書式の外にある");
                }
            }
            for (String w : new java.util.LinkedHashSet<>(bareNames(plain(HEADING_H23.matcher(bodyProse).replaceAll("〓"), true), allIds, java.util.Set.of()))) {
                problems.add(l.id() + " 本文: 識別子「" + w + "」がコード書式の外にある");
            }
            for (Lesson.Sample s : l.samples()) {
                for (String w : new java.util.LinkedHashSet<>(bareNames(plain(s.descriptionHtml(), true), allIds, java.util.Set.of()))) {
                    problems.add(l.id() + " サンプル「" + s.title() + "」の説明: 識別子「" + w + "」がコード書式の外にある");
                }
            }
            // 地の文に絵文字を置かない(最初のレッスンの吹き出しに ☕ があった。design.md の「珈琲の記号と絵文字を外す」。27 回目の審査)。
            // コードブロック・サンプルのコードと出力は対象外(spring-boot-startup の banner の例はコードブロックの中)
            java.util.Map<String, String> prose = new java.util.LinkedHashMap<>();
            prose.put("本文", l.bodyHtml());
            prose.put("説明文", l.summaryHtml());
            for (int i = 0; i < l.goals().size(); i++) {
                prose.put("ゴール " + i, l.goalsHtml().get(i));
            }
            for (int i = 0; i < l.columns().size(); i++) {
                prose.put("コラム " + i, l.columns().get(i).title() + "〓" + l.columns().get(i).bodyHtml());
            }
            if (l.hasSts()) {
                prose.put("STS", l.sts().introHtml());
                for (int i = 0; i < l.sts().steps().size(); i++) {
                    prose.put("STS の手順 " + i, l.sts().steps().get(i).title() + "〓" + l.sts().steps().get(i).detailHtml());
                }
            }
            for (int i = 0; i < l.exercises().size(); i++) {
                prose.put("演習 " + i, l.exercises().get(i).problemHtml());
            }
            for (int i = 0; i < l.samples().size(); i++) {
                prose.put("サンプル " + i, l.samples().get(i).title() + "〓" + l.samples().get(i).descriptionHtml());
            }
            for (int i = 0; i < l.atWork().size(); i++) {
                Lesson.AtWork aw = l.atWork().get(i);
                prose.put("仕事のコード " + i, aw.title() + "〓" + aw.introHtml() + "〓" + String.join("〓", aw.notesHtml().values())
                        + "〓" + String.join("〓", aw.pointsHtml()));
            }
            for (int i = 0; i < l.quiz().size(); i++) {
                var q = l.quiz().get(i);
                prose.put("quiz " + i, texts(q.questionHtml(), q.explanationHtml(), q.choicesHtml(), q.whyHtml(), List.of()));
            }
            for (int i = 0; i < l.reading().size(); i++) {
                var r = l.reading().get(i);
                prose.put("reading " + i, texts(r.questionHtml(), r.explanationHtml(), r.choicesHtml(), r.whyHtml(), r.traceHtml()));
            }
            for (int i = 0; i < l.checks().size(); i++) {
                var c = l.checks().get(i);
                prose.put("check " + i, texts(c.questionHtml(), c.explanationHtml(), c.choicesHtml(), c.whyHtml(), List.of()));
            }
            prose.forEach((where, html) -> {
                String e = emoji(plain(html, true));
                if (e != null) {
                    problems.add(l.id() + " " + where + ": 地の文に絵文字「" + e + "」");
                }
            });
            // 和字にはさまれた太字の前後に空白を置かない(「に **ジャンプ** して」が画面で「に ジャンプ して」と半角空いた。27 回目の審査)。
            // 本文(手を止めてを含む)・問い・解説・コラム・説明のどこでも
            prose.forEach((where, html) -> {
                String gap = boldGap(FIGURE.matcher(html == null ? "" : html).replaceAll("〓").replaceAll("(?s)<pre\\b.*?</pre>", "〓"));
                if (gap != null) {
                    problems.add(l.id() + " " + where + ": 和字にはさまれた太字の前後に空白「" + gap + "」(`に**語**して` と詰める)");
                }
            });
            // 1 行のブロックになる英語のメッセージの直後を「です。」「を読む。」「。」だけにしない(ブロックの下に述語だけの行が残り、
            // 直後が「。」ならチップのまま 3 行に割れる。27 回目の審査)。「次のコンパイルエラーになります: `…`」の前置きの形に
            java.util.Map<String, String> msgPlaces = new java.util.LinkedHashMap<>();
            msgPlaces.put("本文", body);
            for (int i = 0; i < l.columns().size(); i++) {
                msgPlaces.put("コラム " + i, l.columns().get(i).bodyHtml());
            }
            if (l.hasSts()) {
                msgPlaces.put("STS", l.sts().introHtml());
                for (int i = 0; i < l.sts().steps().size(); i++) {
                    msgPlaces.put("STS の手順 " + i, l.sts().steps().get(i).detailHtml());
                }
            }
            for (int i = 0; i < l.exercises().size(); i++) {
                msgPlaces.put("演習 " + i, l.exercises().get(i).problemHtml());
            }
            msgPlaces.forEach((where, html) -> {
                String tail = shortTail(html, CurriculumTest::bodyMsg);
                if (tail != null) {
                    problems.add(l.id() + " " + where + ": メッセージの直後が述語だけ「" + tail + "」(「次のエラーになります: `…`」の前置きの形に)");
                }
            });
            // レッスンの説明文(冒頭の 1〜2 文)もゴールと同じ
            String summaryWord = bareIdentifier(l.summary() == null ? "" : l.summary().replaceAll("`[^`]*`", "〓"), lessonIds);
            if (summaryWord != null) {
                problems.add(l.id() + " 説明文: 識別子「" + summaryWord + "」がコード書式の外にある");
            }
            // 本文の地の文の true・false・null(図の文字は除く)
            java.util.regex.Matcher bodyLit = BARE_LITERAL.matcher(plain((l.bodyHtml() == null ? "" : l.bodyHtml()).replaceAll("(?s)<figure\\b.*?</figure>", "〓"), true));
            if (bodyLit.find()) {
                problems.add(l.id() + " 本文: リテラル「" + bodyLit.group() + "」がコード書式の外にある");
            }
            for (String g : l.goals()) {
                String w = bareIdentifier(g.replaceAll("`[^`]*`", "〓"), lessonIds);
                if (w != null) {
                    problems.add(l.id() + " ゴール: 識別子「" + w + "」がコード書式の外にある(「" + g + "」)");
                }
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
        // 読解はいちばん長いのを選ぶ・いちばん短いのを捨てる、で当てられないように(当てずっぽうなら 4 択で 25%)。
        // 正解が長い側・短い側に来ないこと自体も手がかりになるので、どちらも 20〜32%(27 回目の審査で 18%・19%)
        if (readingBias[1] * 100 < readingBias[0] * 20 || readingBias[1] * 100 > readingBias[0] * 32) {
            problems.add("読解: 正解がいちばん長い選択肢の問が " + readingBias[1] + " / " + readingBias[0] + "(20〜32%)");
        }
        if (readingBias[3] * 100 < readingBias[2] * 20 || readingBias[3] * 100 > readingBias[2] * 32) {
            problems.add("読解: 正解がいちばん短い選択肢の問が " + readingBias[3] + " / " + readingBias[2] + "(20〜32%)");
        }
        // 手を止めては 3 択が多いので、「いちばん短いのを選ぶ」で当たらないように(当てずっぽうなら 33%。27 回目の審査で 42%)
        if (checkBias[1] * 100 > checkBias[0] * 38) {
            problems.add("手を止めて: 正解がいちばん短い選択肢の問が " + checkBias[1] + " / " + checkBias[0] + "(38% まで)");
        }
        for (Term t : CONTENT.glossary()) {
            java.util.regex.Matcher g = GLOSSARY_CODE.matcher(plain(t.defHtml(), true));
            String code = g.find() ? g.group() : bareCode(plain(t.defHtml(), true));
            if (code != null) {
                problems.add("用語集「" + t.term() + "」: コードの字句「" + code + "」がコード書式の外にある");
            }
            String e = emoji(t.term() + "\n" + plain(t.defHtml(), true));
            if (e != null) {
                problems.add("用語集「" + t.term() + "」: 地の文に絵文字「" + e + "」");
            }
            String gap = boldGap(t.defHtml());
            if (gap != null) {
                problems.add("用語集「" + t.term() + "」: 和字にはさまれた太字の前後に空白「" + gap + "」");
            }
        }
        assertThat(problems).as("CONTENT_FORMAT.md の why・引用符・予想の問いの決まり").isEmpty();
    }

    @Test
    @DisplayName("用語集で lesson のある語は、候補(「A / B」の両方・括弧の中の言い換え)のどれかが、そのレッスンの本文・仕事のコード・サンプルに出る")
    void glossaryTermsLand() {
        // 用語集の「〇〇 で」は、本文(段落・箇条書き・見出し・表・図の説明と太字)→ 仕事のコード(説明・行の説明・要点)→ サンプル(題・説明)の
        // 順に語の候補を探して着く(アプリの findTerm)。どこにも無いと、レッスンの頭に着いて「同じ言い方では出てきません」と出る
        // (400 語のうち 71 語がそうだった。27 回目の審査)
        List<String> misses = new java.util.ArrayList<>();
        for (Term t : CONTENT.glossary()) {
            if (t.lessonId() == null) {
                continue;
            }
            Lesson l = CONTENT.lesson(t.lessonId()).orElseThrow();
            List<String> words = termWords(t.term());
            String body = CHECK_ASIDE.matcher(l.bodyHtml() == null ? "" : l.bodyHtml()).replaceAll("");
            List<String> places = new java.util.ArrayList<>(elementTexts(body, java.util.Set.of("p", "li", "h2", "h3", "h4", "td", "th", "figcaption")));
            for (Lesson.AtWork aw : l.atWork()) {
                places.add(landText(aw.introHtml()));
                aw.notesHtml().values().forEach(n -> places.add(landText(n)));
                aw.pointsHtml().forEach(p -> places.add(landText(p)));
            }
            for (Lesson.Sample s : l.samples()) {
                places.add(landText(s.title()));
                places.add(landText(s.descriptionHtml()));
            }
            boolean bold = elementTexts(body, java.util.Set.of("strong")).stream().anyMatch(words::contains);
            if (!bold && places.stream().noneMatch(p -> words.stream().anyMatch(p::contains))) {
                misses.add(t.term() + "(" + t.lessonId() + "。候補 " + words + ")");
            }
        }
        assertThat(misses).as("用語集の語が、lesson のレッスンの本文・仕事のコード・サンプルに無い(本文の言い方をそろえるか、lesson を付け替えるか、括弧に別名を足す)").isEmpty();
    }

    /** 1 問の地の文(問い・解説・選択肢・why・追跡)を 1 つの HTML に */
    private static String texts(String question, String explanation, List<String> choices, List<String> why, List<String> trace) {
        List<String> all = new java.util.ArrayList<>(List.of(question == null ? "" : question, explanation == null ? "" : explanation));
        all.addAll(choices);
        all.addAll(why);
        all.addAll(trace);
        return String.join("〓", all);
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

    private static final java.util.regex.Pattern BARE_LITERAL = java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])(?:true|false|null)(?![A-Za-z0-9_])");
    private static final java.util.regex.Pattern BARE_JAVA_WORD = java.util.regex.Pattern.compile(
            "(?<![A-Za-z0-9_@.\\-])(?:true|false|null|case|switch|default|static|final|new|return|extends|implements|break|continue|void"
            + "|int|long|double|float|boolean|char|byte|short|this|super"
            // 制御・例外・型の宣言の予約語も(「両方の catch が実行される」「while と同じく」が地の文の書体だった。27 回目の審査)
            + "|while|if|else|for|do|try|catch|finally|throw|throws|class|interface|enum|record|import|package"
            + "|abstract|public|private|protected|instanceof)(?![A-Za-z0-9_\\-])");
    /** 問いのコードの 1〜2 字の名前のうち、地の文に英語として書いてよいもの(スタックトレースの at 行・Caused by など) */
    private static final java.util.Set<String> SHORT_ENGLISH = java.util.Set.of("at", "by", "of", "to", "in", "is", "or", "on", "an", "as");
    /** 層や役割の一般名(Service 層・Bean・Entity・Builder パターン)。コードに同じ名前があっても地の文のまま */
    private static final java.util.Set<String> ROLE_WORDS = java.util.Set.of("Service", "Controller", "Repository", "Bean", "Entity", "Builder");
    /** メソッド名と見なさない予約語(コードで直後に「(」が来るもの) */
    private static final java.util.Set<String> JAVA_KEYWORDS = java.util.Set.of("if", "for", "while", "switch", "catch", "synchronized", "return",
            "new", "super", "this", "assert", "throw", "yield", "try", "do", "else", "case", "default", "instanceof");
    private static final java.util.regex.Pattern METHOD_CALL = java.util.regex.Pattern.compile("(?<![A-Za-z0-9_$])([a-z][A-Za-z0-9_]*)\\s*\\(");
    private static final java.util.regex.Pattern JAVA_BLOCK = java.util.regex.Pattern.compile("(?s)<pre><code class=\"language-java\">(.*?)</code></pre>");
    private static final java.util.regex.Pattern CHECK_ASIDE = java.util.regex.Pattern.compile("(?s)<aside class=\"check\".*?</aside>");
    private static final java.util.regex.Pattern FIGURE = java.util.regex.Pattern.compile("(?s)<figure\\b.*?</figure>");
    private static final java.util.regex.Pattern HEADING_H23 = java.util.regex.Pattern.compile("(?s)<h([23])\\b[^>]*>(.*?)</h\\1>");
    /** 絵文字と記号(☕・✅ など)。U+2600〜27BF と U+1F300〜1FAFF */
    private static final java.util.regex.Pattern EMOJI = java.util.regex.Pattern.compile("[\\x{2600}-\\x{27BF}\\x{1F300}-\\x{1FAFF}]");
    /** 和字(ひらがな・カタカナ・漢字)。「・」(U+30FB)は約物なので除く */
    private static final String WAJI = "[\\u3041-\\u309F\\u30A0-\\u30FA\\u30FC-\\u30FF\\u3400-\\u4DBF\\u4E00-\\u9FFF\\u3005]";
    private static final java.util.regex.Pattern STRONG = java.util.regex.Pattern.compile("(?s)<strong>(.*?)</strong>");
    private static final java.util.regex.Pattern INLINE_CODE_ANY = java.util.regex.Pattern.compile("<code>([^<]*)</code>");
    /** メッセージの直後が「。」か、5 字以下の述語で文が終わる(「です。」「を読む。」) */
    private static final java.util.regex.Pattern SHORT_TAIL = java.util.regex.Pattern.compile("^\\s*(?:。|[^\\s。、]{1,5}。)");
    /** 英語の文らしいかを数えるとき、英単語に数えない予約語(スマホ版の JAVA_WORDS と同じ) */
    private static final java.util.Set<String> MSG_JAVA_WORDS = java.util.Set.of(("public private protected static final abstract void int long double float "
            + "boolean byte char short var new return if else for while do switch case default try catch finally throw throws class interface record "
            + "enum extends implements super this null true false instanceof import package").split(" "));
    /** エラーの 1 行(スマホ版の ERROR_LINE と同じ) */
    private static final java.util.regex.Pattern ERROR_LINE = java.util.regex.Pattern.compile(
            "\\b[A-Z]\\w*(?:Exception|Error)\\b:|^(?:Caused by|Resolved|WARN|ERROR|Exception in thread)\\b");
    /** 宣言・総称型・キャスト(スマホ版の DECLARATION と同じ。メッセージではなくコード) */
    private static final java.util.regex.Pattern DECLARATION = java.util.regex.Pattern.compile(
            "\\s=\\s|;\\s*$|\\b(?:extends|implements|instanceof|permits)\\b|\\bnew [A-Z]\\w*[(<\\[]|<[A-Z?]|^\\(|^(?:(?:public|private|protected|static|final|abstract|sealed)\\s+)*(?:class|interface|record|enum)\\b");
    private static final java.util.regex.Pattern TAG_NAME = java.util.regex.Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*>");
    private static final java.util.regex.Pattern IDENT = java.util.regex.Pattern.compile(
            "(?<![A-Za-z0-9_@.])@?[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*");
    private static final java.util.Set<String> CODE_KEYWORDS = java.util.Set.of("final", "static", "null", "true", "false");
    /** 追跡の地の文に英語として書いてよい小文字の語(コードの名前と同じ綴りでも) */
    private static final java.util.Set<String> ENGLISH_WORDS = java.util.Set.of("of", "to", "in", "is", "and", "or", "the", "a", "an", "at", "on", "by",
            "class", "yml", "yaml", "xml", "json"); // 「class ファイル」「yml にない」はファイルの用語

    /**
     * コードに出る名前(ドットでつないだ名前は、つないだままと 1 つずつの両方)。文字列とコメントの中の語は名前に数えない
     * (サンプルの出力の文字「IoC コンテナ」まで、説明文でコードにすることになった)
     */
    static java.util.Set<String> codeIdentifiers(String code) {
        String src = (code == null ? "" : code).replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ").replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
        java.util.Set<String> ids = new java.util.HashSet<>();
        java.util.regex.Matcher m = IDENT.matcher(src);
        while (m.find()) {
            ids.add(m.group());
            ids.addAll(List.of(m.group().replace("@", "").split("\\.")));
        }
        return ids;
    }

    /**
     * 地の文に残ったコードの語: 注釈(@Component)・完全修飾名(java.time.Clock)・final / static / null / true / false と、
     * コードに出る名前のうち、ドットでつないだもの・大文字を 2 つ以上持つもの(RestTemplate)・途中に大文字のあるもの(findById)・定数(MAX_SIZE)。
     * 大文字 1 つの語(List・Service・Bean)は、役割の言葉として地の文に書くこともあるので拾わない
     */
    static String bareIdentifier(String text, java.util.Set<String> ids) {
        java.util.regex.Matcher m = IDENT.matcher(text == null ? "" : text);
        while (m.find()) {
            String t = m.group();
            boolean shaped = t.contains(".") || t.matches("[A-Z][a-z0-9]+(?:[A-Z][A-Za-z0-9]*)+") || t.matches("[a-z][a-z0-9]*[A-Z]\\w*")
                    || t.matches("[A-Z][A-Z0-9]*_[A-Z0-9_]+");
            if ((t.startsWith("@") && t.length() > 1) || t.matches("[a-z]\\w*(?:\\.[a-z]\\w*)+\\.[A-Z]\\w*") || CODE_KEYWORDS.contains(t)
                    || (shaped && ids.contains(t))) {
                return t;
            }
        }
        return null;
    }

    /**
     * 本文・サンプルの説明・仕事のコードの説明の地の文に残ったコードの名前(見つけた順にすべて): 注釈・完全修飾名と、
     * レッスンのコードに出る名前のうち、大文字で始まり小文字を含むもの(List・Notifier・RuntimeException)・ドットでつないだもの・
     * 途中に大文字のあるもの(findById)・定数(MAX_SIZE)と、methods に入っているメソッド名(見出しで使う)。
     * 層の一般名(Service・Repository)と、英語の名前の一部(Bean Validation・Hello World・Stream API)は地の文のまま
     */
    static List<String> bareNames(String text, java.util.Set<String> ids, java.util.Set<String> methods) {
        List<String> found = new java.util.ArrayList<>();
        String p = text == null ? "" : text;
        java.util.regex.Matcher m = IDENT.matcher(p);
        while (m.find()) {
            String t = m.group();
            boolean camel = t.matches("[A-Z]\\w*") && t.matches(".*[a-z].*") && !ROLE_WORDS.contains(t) && !nextToEnglish(p, m.start(), m.end());
            boolean shaped = t.contains(".") || t.matches("[a-z][a-z0-9]*[A-Z]\\w*") || t.matches("[A-Z][A-Z0-9]*_[A-Z0-9_]+");
            if ((t.startsWith("@") && t.length() > 1) || t.matches("[a-z]\\w*(?:\\.[a-z]\\w*)+\\.[A-Z]\\w*")
                    || ((camel || shaped || methods.contains(t)) && ids.contains(t))) {
                found.add(t);
            }
        }
        return found;
    }

    /** コードで直後に「(」が来る名前(メソッド名。予約語は除く)。文字列とコメントの中は数えない */
    static java.util.Set<String> methodNames(String code) {
        String src = (code == null ? "" : code).replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ").replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
        java.util.Set<String> names = new java.util.HashSet<>();
        java.util.regex.Matcher m = METHOD_CALL.matcher(src);
        while (m.find()) {
            if (!JAVA_KEYWORDS.contains(m.group(1))) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    /** HTML の中の Java のコードブロック(```java)の文字 */
    static String javaBlocks(String html) {
        StringBuilder b = new StringBuilder();
        java.util.regex.Matcher m = JAVA_BLOCK.matcher(html == null ? "" : html);
        while (m.find()) {
            b.append(org.springframework.web.util.HtmlUtils.htmlUnescape(m.group(1).replaceAll("<[^>]+>", ""))).append('\n');
        }
        return b.toString();
    }

    /** 英語の句の中の語(前後とも英単語。STS のメニュー「Check out new branch」) */
    static boolean inEnglish(String p, int start, int end) {
        return p.substring(0, start).matches("(?s).*[A-Za-z]{2,} $") && p.substring(end).matches("(?s)^ [A-Za-z]{2,}.*");
    }

    /** 英語の名前の一部(前か後ろが英単語。Bean Validation・Hibernate Validator・Run As > JUnit Test) */
    static boolean nextToEnglish(String p, int start, int end) {
        return p.substring(0, start).matches("(?s).*[A-Za-z]{2,} $") || p.substring(end).matches("(?s)^ [A-Za-z]{2,}.*");
    }

    /** 絵文字があれば、その字と前後(無ければ null) */
    static String emoji(String plainText) {
        String p = plainText == null ? "" : plainText;
        java.util.regex.Matcher m = EMOJI.matcher(p);
        return m.find() ? p.substring(Math.max(0, m.start() - 12), Math.min(p.length(), m.end() + 4)).replace('\n', ' ') : null;
    }

    private static boolean waji(char c) {
        return String.valueOf(c).matches(WAJI);
    }

    /**
     * 和字にはさまれた太字の前後の空白(「に <strong>ジャンプ</strong> して」は画面で半角の空きになる)。中身の端と外の隣が和字で、
     * 中身が約物やコードで始まり・終わらないものだけ(**Bean** のような ASCII の語は、和字と英字の間の空白として残す)
     */
    static String boldGap(String html) {
        String s = html == null ? "" : html;
        java.util.regex.Matcher m = STRONG.matcher(s);
        while (m.find()) {
            String inner = m.group(1);
            if (inner.isBlank() || inner.startsWith("<") || inner.endsWith(">")) {
                continue;
            }
            String text = org.springframework.web.util.HtmlUtils.htmlUnescape(inner.replaceAll("<[^>]+>", ""));
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if (!Character.isLetterOrDigit(first) || !Character.isLetterOrDigit(last)) {
                continue;
            }
            int before = m.start();
            while (before > 0 && Character.isWhitespace(s.charAt(before - 1))) {
                before--;
            }
            int after = m.end();
            while (after < s.length() && Character.isWhitespace(s.charAt(after))) {
                after++;
            }
            boolean left = before < m.start() && before > 0 && waji(s.charAt(before - 1)) && waji(first);
            boolean right = after > m.end() && after < s.length() && waji(s.charAt(after)) && waji(last);
            if (left || right) {
                return plain(s.substring(Math.max(0, before - 6), Math.min(s.length(), after + 6)), false).replace('\n', ' ');
            }
        }
        return null;
    }

    /** 英語の文らしいか(スマホ版の proseLike と同じ): 予約語でない英単語が 3 つ続くか、先頭が 2 語(「Cannot invoke」「Square is」) */
    static boolean proseLike(String t) {
        String[] words = t.strip().split("\\s+");
        int run = 0;
        for (String w : words) {
            run = plainWord(w) ? run + 1 : 0;
            if (run >= 3) {
                return true;
            }
        }
        return plainWord(words[0]) && words.length > 1 && plainWord(words[1]);
    }

    private static boolean plainWord(String w) {
        return w.matches("[A-Za-z][a-z]+(?:-[a-z]+)*") && !MSG_JAVA_WORDS.contains(w);
    }

    /**
     * 本文で 1 行のブロックにするメッセージ(スマホ版の bodyMsg と同じ): 24 字以上で空白を含み、宣言・式の形(` = `・`;` 終わり・extends・
     * `new X(`・`<T`・先頭の「(」・class の宣言)でなく、英語の文らしいもの・大文字だけの文・エラーの 1 行(`…Exception:`・Caused by)
     */
    static boolean bodyMsg(String t) {
        return t.length() >= 24 && t.strip().matches("(?s).*\\s.*") && !DECLARATION.matcher(t).find()
                && (proseLike(t) || t.strip().matches("[A-Z]{2,}(?: [A-Z]{2,}){2,}") || ERROR_LINE.matcher(t).find());
    }

    /**
     * 解説で 1 行のブロックにするメッセージ(スマホ版の msgLike と同じ): 24 字以上で空白を含み、文や宣言の記号(; { } = && || ->)が無い。
     * 呼び出しの括弧があるものは英語の文らしいときだけ
     */
    static boolean explainMsg(String t) {
        return t.length() >= 24 && t.strip().matches("(?s).*\\s.*") && !java.util.regex.Pattern.compile("[;{}=]|&&|\\|\\||->").matcher(t).find()
                && (!java.util.regex.Pattern.compile("\\w\\(").matcher(t).find() || proseLike(t));
    }

    /**
     * メッセージ(isMessage)の直後が「。」か、5 字以下の述語だけで文が終わる所(画面ではメッセージが 1 行のブロックになり、
     * 「です。」「。」だけの行が残るか、チップのまま 3 行に割れる)。無ければ null
     */
    static String shortTail(String html, java.util.function.Predicate<String> isMessage) {
        String s = (html == null ? "" : html).replaceAll("(?s)<pre\\b.*?</pre>", "〓");
        java.util.regex.Matcher m = INLINE_CODE_ANY.matcher(s);
        while (m.find()) {
            String t = org.springframework.web.util.HtmlUtils.htmlUnescape(m.group(1));
            if (!isMessage.test(t)) {
                continue;
            }
            String after = org.springframework.web.util.HtmlUtils.htmlUnescape(s.substring(m.end(), Math.min(s.length(), m.end() + 300)).replaceAll("<[^>]+>", ""));
            java.util.regex.Matcher tail = SHORT_TAIL.matcher(after);
            if (tail.find()) {
                return t + tail.group().strip();
            }
        }
        return null;
    }

    /** 用語集の語の候補(アプリの termWords と同じ): 括弧の外を「/」「／」で分けたもの + 括弧の中を「/」「／」「、」「,」で分けたもの。空白は 1 つに、小文字で */
    static List<String> termWords(String term) {
        List<String> alt = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((.*?)\\)").matcher(term);
        StringBuilder base = new StringBuilder();
        while (m.find()) {
            alt.addAll(List.of(m.group(1).split("\\s*[/／、,]\\s*")));
            m.appendReplacement(base, "");
        }
        m.appendTail(base);
        List<String> words = new java.util.ArrayList<>(List.of(base.toString().strip().split("\\s*[/／]\\s*")));
        words.addAll(alt);
        return words.stream().map(w -> w.replaceAll("\\s+", " ").strip().toLowerCase(java.util.Locale.ROOT)).filter(w -> !w.isEmpty()).toList();
    }

    /** 着地で比べる文字(アプリの plainText と同じ): タグを外し、語をつなぐ印を消し、空白を 1 つにまとめて小文字に */
    static String landText(String html) {
        return org.springframework.web.util.HtmlUtils.htmlUnescape((html == null ? "" : html).replaceAll("<[^>]+>", ""))
                .replaceAll("[\\u2060\\u00ad]", "").replaceAll("\\s+", " ").strip().toLowerCase(java.util.Locale.ROOT);
    }

    /** HTML の中の、名前が names の要素ごとの文字(textContent と同じく、入れ子の要素の文字も含む) */
    static List<String> elementTexts(String html, java.util.Set<String> names) {
        List<String> out = new java.util.ArrayList<>();
        java.util.Deque<java.util.Map.Entry<String, Integer>> open = new java.util.ArrayDeque<>();
        java.util.regex.Matcher m = TAG_NAME.matcher(html == null ? "" : html);
        while (m.find()) {
            String name = m.group(2).toLowerCase(java.util.Locale.ROOT);
            if (!names.contains(name)) {
                continue;
            }
            if (m.group(1).isEmpty()) {
                open.push(java.util.Map.entry(name, m.end()));
                continue;
            }
            // 同じ名前のいちばん内側の開きを閉じる
            for (java.util.Iterator<java.util.Map.Entry<String, Integer>> it = open.iterator(); it.hasNext(); ) {
                java.util.Map.Entry<String, Integer> o = it.next();
                if (o.getKey().equals(name)) {
                    it.remove();
                    out.add(landText(html.substring(o.getValue(), m.start())));
                    break;
                }
            }
        }
        return out;
    }

    private static void proseProblems(String where, String question, List<String> choices, List<String> why, String explanation,
                                      List<String> trace, String questionCode, List<String> problems) {
        List<String> texts = new java.util.ArrayList<>(List.of(question, explanation));
        texts.addAll(choices);
        texts.addAll(why);
        // true・false・null と Java の予約語・基本型(case・static・int・catch・enum…)はどこでもコード書式に
        // (問いをまたいで 1 画面に並ぶと、書体が揺れて見えた。「`switch` 文」「`default` メソッド」「`enum` 型」も)
        List<String> withTrace = new java.util.ArrayList<>(texts);
        withTrace.addAll(trace);
        for (String t : withTrace) {
            String p = plain(t, true);
            java.util.regex.Matcher lit = BARE_JAVA_WORD.matcher(p);
            while (lit.find()) {
                // 英語の句の中の語(STS のメニュー「Check out new branch」)は地の文のまま
                if (!inEnglish(p, lit.start(), lit.end())) {
                    problems.add(where + ": コードの語「" + lit.group() + "」がコード書式の外にある");
                    break;
                }
            }
        }
        // 問いのコードに出る 1〜2 字の名前(`n`・`i`・`a`)も地の文ではコード書式に(「n が増え続けて」と、すぐ上のコードの `n` が
        // 別の書体で出ていた。27 回目の審査)。コードは問いの Java のブロックと読解のコード(出力・HTML のブロックの文字は名前ではない)
        java.util.Set<String> shortNames = new java.util.HashSet<>();
        for (String id : codeIdentifiers((questionCode == null ? "" : questionCode) + "\n" + javaBlocks(question))) {
            if (id.length() <= 2 && !SHORT_ENGLISH.contains(id) && !BARE_JAVA_WORD.matcher(id).matches()) {
                shortNames.add(id);
            }
        }
        for (String t : withTrace) {
            String p = plain(t, true);
            java.util.regex.Matcher n = IDENT.matcher(p);
            while (n.find()) {
                if (shortNames.contains(n.group()) && !inEnglish(p, n.start(), n.end())) {
                    problems.add(where + ": コードの名前「" + n.group() + "」がコード書式の外にある(「" + p.strip().replace('\n', ' ') + "」)");
                    break;
                }
            }
        }
        // 解説で 1 行のブロックになるメッセージの直後を「。」や「です。」だけにしない(述語だけの行が残る。前置きの形に)
        String tail = shortTail(explanation, t -> explainMsg(t) || bodyMsg(t));
        if (tail != null) {
            problems.add(where + " 解説: メッセージの直後が述語だけ「" + tail + "」(「次のエラーになります: `…`」の前置きの形に)");
        }
        // why と選択肢は 1 文。行頭の「+ 」「- 」で Markdown の箇条書きや見出しに化けると、記号が消えて文の意味が変わる
        List<String> oneLine = new java.util.ArrayList<>(choices);
        oneLine.addAll(why);
        for (String t : oneLine) {
            if (t != null && java.util.regex.Pattern.compile("<(?:ul|ol|li|h[1-6]|blockquote)\\b").matcher(t).find()) {
                problems.add(where + ": 選択肢か why が箇条書き・見出しに化けている(行頭の記号はコード書式に): " + plain(t, true));
            }
        }
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

    @Test
    @DisplayName("演習の雛形とサンプルのコメントは、PC の編集欄で横に送らずに読める(コメントだけの行は 72 列、行末にコメントのある行は 80 列まで。和字は 2 列)")
    void commentsFitEditor() {
        // PC の編集欄は 1280px で 1 行 82 列(16px の Cascadia Code。和字は 1.7 列)。TODO の指示が右で切れ、書く人が横に送って読んでいた
        // (29 回目の審査。1280px で 54 本)。コメントだけの行は意味の切れ目で折れるので 72 列まで。コードの後ろのコメントはコードの長さに
        // 引きずられるので 80 列まで(超えるならコメントを上の行へ移す)。コメントでないコードの行は数えない
        List<String> problems = new java.util.ArrayList<>();
        CONTENT.allExercises().forEach(e -> commentWidthProblems(e.key() + " の雛形", e.starter(), problems));
        for (Lesson l : CONTENT.readyLessons()) {
            for (Lesson.Sample s : l.samples()) {
                commentWidthProblems(l.id() + " / " + s.title(), s.code(), problems);
            }
        }
        assertThat(problems).as("編集欄で右が切れるコメント(折るか、行末のコメントを上の行へ移す)").isEmpty();
    }

    private static void commentWidthProblems(String where, String code, List<String> problems) {
        boolean[] textBlock = { false };
        String[] lines = code.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].stripTrailing();
            String s = line.strip();
            boolean commentOnly = !textBlock[0] && (s.startsWith("//") || s.startsWith("/*") || s.startsWith("*"));
            int at = commentOnly ? -1 : lineCommentAt(line, textBlock);
            int w = displayWidth(line);
            if (commentOnly && w > 72 || at >= 0 && w > 80) {
                problems.add(where + " の " + (i + 1) + " 行目(" + w + " 列): " + s);
            }
        }
    }

    /** 行の中の行末コメント(// か /*)の位置。文字列・文字・テキストブロックの中は除く。無ければ -1。textBlock[0] は行をまたいで持つ */
    private static int lineCommentAt(String line, boolean[] textBlock) {
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            if (textBlock[0]) {
                if (line.startsWith("\"\"\"", i)) {
                    textBlock[0] = false;
                    i += 2;
                }
                continue;
            }
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (line.startsWith("\"\"\"", i)) {
                textBlock[0] = true;
                i += 2;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (line.startsWith("//", i) || line.startsWith("/*", i)) {
                return i;
            }
        }
        return -1;
    }

    /** 等幅の書体で何列ぶんか(East Asian Width が W・F の字を 2 列。矢印・丸数字などのあいまいな字は 1 列) */
    static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            boolean wide = cp >= 0x1100 && cp <= 0x115F || cp >= 0x2E80 && cp <= 0x303E || cp >= 0x3041 && cp <= 0x33FF
                    || cp >= 0x3400 && cp <= 0x4DBF || cp >= 0x4E00 && cp <= 0x9FFF || cp >= 0xA000 && cp <= 0xA4CF
                    || cp >= 0xAC00 && cp <= 0xD7A3 || cp >= 0xF900 && cp <= 0xFAFF || cp >= 0xFE30 && cp <= 0xFE4F
                    || cp >= 0xFF00 && cp <= 0xFF60 || cp >= 0xFFE0 && cp <= 0xFFE6 || cp >= 0x1F300 && cp <= 0x1FAFF;
            w += wide ? 2 : 1;
        }
        return w;
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
