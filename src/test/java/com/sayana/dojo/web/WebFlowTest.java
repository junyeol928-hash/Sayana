package com.sayana.dojo.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.test.web.servlet.MockMvc;

import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.ExerciseDef;
import com.sayana.dojo.content.Lesson;
import com.sayana.dojo.progress.GeneratedExercise;
import com.sayana.dojo.progress.GeneratedExerciseRepository;
import com.sayana.dojo.progress.ProgressService;
import com.sayana.dojo.progress.ReviewItem;
import com.sayana.dojo.progress.ReviewItemRepository;
import com.sayana.dojo.progress.SubmissionRepository;
import com.sayana.dojo.support.MutableClock;
import com.sayana.dojo.support.TestClockConfig;

/**
 * 画面の通しテスト(APIキーなし=AI機能オフの状態)。
 * 「読む→動かす→書く→確かめる→復習」が1レッスンの中で回ることを確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class WebFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ContentRepository content;
    @Autowired
    ProgressService progress;
    @Autowired
    ReviewItemRepository reviewItems;
    @Autowired
    MutableClock clock;
    @Autowired
    GeneratedExerciseRepository generated;
    @Autowired
    SubmissionRepository submissions;

    @Test
    @DisplayName("主要な画面が表示できる")
    void pages() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("第1部 Java基礎")))
                .andExpect(content().string(containsString("染まり具合")));
        mvc.perform(get("/reading")).andExpect(status().isOk()).andExpect(content().string(containsString("1 行ずつの追跡")));
        mvc.perform(get("/glossary")).andExpect(status().isOk()).andExpect(content().string(containsString("用語集")));
        mvc.perform(get("/lessons/java-classes")).andExpect(status().isOk())
                .andExpect(content().string(containsString("仕事のコードではこう出てくる")))
                .andExpect(content().string(containsString("このレッスンの用語")));
        mvc.perform(get("/lessons/java-hello")).andExpect(status().isOk())
                .andExpect(content().string(containsString("STSでやってみよう")))
                .andExpect(content().string(containsString("AI は未設定です")))
                .andExpect(content().string(not(containsString("AI OFF"))));
        // 準備中のレッスンが残っていれば、その表示も確かめる
        var planned = content.allLessons().stream().filter(l -> !l.ready()).findFirst();
        if (planned.isPresent()) {
            mvc.perform(get("/lessons/" + planned.get().id())).andExpect(status().isOk())
                    .andExpect(content().string(containsString("準備中")));
        }
        mvc.perform(get("/lessons/spring-di")).andExpect(status().isOk())
                .andExpect(content().string(containsString("DIとIoCコンテナ")));
        mvc.perform(get("/lessons/no-such-lesson")).andExpect(status().isNotFound());
        mvc.perform(get("/review")).andExpect(status().isOk());
        mvc.perform(get("/history")).andExpect(status().isOk());
        mvc.perform(get("/playground")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("1レッスンの流れ:読む→動かす→書く→確かめる→復習")
    void lessonFlow() throws Exception {
        Lesson lesson = content.lesson("java-loops").orElseThrow();

        // 読む
        // 「読了」の 1 行は本文のすぐ下の段を指す(いつも「サンプルを動かして」だった。30 回目の審査)
        mvc.perform(post("/lessons/java-loops/read")).andExpect(status().isOk())
                .andExpect(content().string(containsString(lesson.afterReadHint())));
        assertThat(lesson.afterReadHint()).isEqualTo(lesson.atWork().isEmpty()
                ? "読了。次は下のサンプルを動かしてみよう" : "読了。次は下の仕事のコードを読んでみよう");
        // 動かす
        mvc.perform(post("/run").param("code", lesson.samples().get(0).code()).param("lessonId", "java-loops"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("実行しました")));
        // 書く(全演習を模範解答で提出)
        for (ExerciseDef ex : lesson.exercises()) {
            mvc.perform(post("/exercises/submit").param("key", ex.key()).param("code", ex.solution()))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("合格")));
        }
        // 確かめる(全問正解)
        var req = post("/lessons/java-loops/quiz");
        for (int i = 0; i < lesson.quiz().size(); i++) {
            req = req.param("q" + i, String.valueOf(lesson.quiz().get(i).answer()));
        }
        mvc.perform(req).andExpect(status().isOk()).andExpect(content().string(containsString("満点")));

        var status = progress.status(lesson);
        assertThat(status.read()).isTrue();
        assertThat(status.sampleRun()).isTrue();
        assertThat(status.completed()).isTrue();

        // 1日後に復習キューに出てくる
        long dueToday = reviewItems.findAll().stream().filter(i -> i.getLessonId().equals("java-loops")).count();
        assertThat(dueToday).isEqualTo(lesson.quiz().size() + lesson.exercises().size());
        clock.advanceDays(1);
        mvc.perform(get("/review")).andExpect(status().isOk()).andExpect(content().string(containsString("残り")));

        ReviewItem quizItem = reviewItems.findAll().stream()
                .filter(i -> i.getItemType() == ReviewItem.Type.QUIZ && i.getLessonId().equals("java-loops"))
                .findFirst().orElseThrow();
        int answer = lesson.quiz().get(quizItem.quizIndex()).answer();
        mvc.perform(post("/review/" + quizItem.getId() + "/quiz").param("choice", String.valueOf(answer)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("次は 3 日後")));

        // 提出履歴から見返せる
        mvc.perform(get("/history").param("lesson", "java-loops")).andExpect(status().isOk())
                .andExpect(content().string(containsString(lesson.exercises().get(0).title())));
        long submissionId = submissions.findByLessonIdOrderByCreatedAtDesc("java-loops").get(0).getId();
        mvc.perform(get("/history/" + submissionId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("提出したコード")));
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("最近の提出")));
        clock.advanceDays(-1);
    }

    @Test
    @DisplayName("雛形のまま提出すると不合格で、失敗理由が表示される")
    void failingSubmission() throws Exception {
        ExerciseDef ex = content.exercise("java-arrays/ex1").orElseThrow();
        mvc.perform(post("/exercises/submit").param("key", ex.key()).param("code", ex.starter()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("不合格")))
                .andExpect(content().string(containsString("✘")));
    }

    @Test
    @DisplayName("STSのチェックリストを付け外しできる")
    void checklist() throws Exception {
        String key = content.lesson("java-hello").orElseThrow().checklist().get(0).key();
        mvc.perform(post("/lessons/java-hello/checklist").param("key", key))
                .andExpect(status().isOk()).andExpect(content().string(containsString("aria-pressed=\"true\"")));
        mvc.perform(post("/lessons/java-hello/checklist").param("key", key))
                .andExpect(status().isOk()).andExpect(content().string(containsString("aria-pressed=\"false\"")));
    }

    @Test
    @DisplayName("APIキー未設定でもAI機能のエンドポイントは落ちず、設定方法を案内する")
    void aiDisabled() throws Exception {
        mvc.perform(post("/ai/chat").param("lessonId", "java-hello").param("question", "質問"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("ANTHROPIC_API_KEY")));
        mvc.perform(post("/exercises/hint").param("key", "java-hello/ex1"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("ANTHROPIC_API_KEY")));
        mvc.perform(post("/ai/explain-run").param("code", "int x = ;").param("error", "エラー"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("ANTHROPIC_API_KEY")));
        mvc.perform(post("/ai/generate").param("lessonId", "java-hello"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("ANTHROPIC_API_KEY")));
    }

    @Test
    @DisplayName("AIが作った類題のページで解いて採点できる")
    void generatedPractice() throws Exception {
        ExerciseDef base = content.exercise("java-if/ex1").orElseThrow();
        GeneratedExercise g = generated.save(new GeneratedExercise("java-if", "類題テスト", base.className(), 1,
                "<script>alert(1)</script> を返す問題", base.starter(), base.solution(), base.test(), "test",
                null, Instant.now()));
        mvc.perform(get("/practice/" + g.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("類題テスト")))
                .andExpect(content().string(containsString("&lt;script&gt;")));
        mvc.perform(post("/exercises/submit").param("key", "gen/" + g.getId()).param("code", base.solution()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("合格")));
        mvc.perform(get("/lessons/java-if")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/practice/" + g.getId())));
    }

    @Test
    @DisplayName("今日まちがえたクイズは、終わりの画面から記録と予定を変えずに解き直せる。終わりの行はどう読み違えたかを言う")
    void retryWrongAnswersSameDay() throws Exception {
        // ほかのテストの問題が期日にあると終わりの画面にならないので、この間だけ先へ送る(最後に戻す)
        var others = reviewItems.findAll();
        var saved = new java.util.HashMap<Long, java.time.LocalDate>();
        others.forEach(it -> saved.put(it.getId(), it.getDueDate()));
        LocalDate today = LocalDate.now(clock);
        try {
            others.forEach(it -> it.setDueDate(today.plusYears(1)));
            reviewItems.saveAll(others);
            Lesson hello = content.lesson("java-hello").orElseThrow();
            Lesson.QuizQuestion q = hello.quiz().get(0);
            int wrong = (q.answer() + 1) % q.choicesHtml().size();
            ReviewItem item = reviewItems.findByItemTypeAndItemRef(ReviewItem.Type.QUIZ, "java-hello#0")
                    .orElseGet(() -> reviewItems.save(new ReviewItem(ReviewItem.Type.QUIZ, "java-hello#0", "java-hello", today, Instant.now(clock))));
            item.setDueDate(today);
            item.setGraduated(false);
            reviewItems.save(item);

            mvc.perform(post("/review/" + item.getId() + "/quiz").param("choice", String.valueOf(wrong))).andExpect(status().isOk());
            ReviewItem after = reviewItems.findById(item.getId()).orElseThrow();
            // 行には why の 1 文目を HTML のまま(<code> だけ残して)出す
            String whyHead = q.whyHtml().get(wrong).split("。")[0].replaceAll("<(?!/?code\\b)[^>]*>", "");
            mvc.perform(get("/review")).andExpect(status().isOk())
                    .andExpect(content().string(containsString("いま解き直す")))
                    .andExpect(content().string(containsString("あなたの読み")))
                    .andExpect(content().string(containsString(whyHead)))
                    .andExpect(content().string(containsString("/lessons/java-hello?from=review#quiz-q0")));
            mvc.perform(get("/review").param("retry", "0")).andExpect(status().isOk())
                    .andExpect(content().string(containsString("解き直し <b>1</b>")))
                    .andExpect(content().string(containsString("unshin baste")));
            // 解き直しの答えは記録しない(段階・期日・今日の数がそのまま)
            int todayBefore = Integer.parseInt(mvc.perform(get("/review")).andReturn().getResponse().getContentAsString()
                    .replaceAll("(?s).*今日 (\\d+) 問のうち.*", "$1"));
            mvc.perform(post("/review/" + item.getId() + "/quiz").param("choice", String.valueOf(q.answer()))
                            .param("retry", "0").param("total", "1"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("/lessons/java-hello?from=review#quiz-q0")))
                    .andExpect(content().string(containsString("おわりの画面へ")))
                    .andExpect(content().string(not(containsString("hx-swap-oob"))));
            ReviewItem unchanged = reviewItems.findById(item.getId()).orElseThrow();
            assertThat(unchanged.getStage()).isEqualTo(after.getStage());
            assertThat(unchanged.getDueDate()).isEqualTo(after.getDueDate());
            assertThat(unchanged.getLastCorrect()).isFalse();
            mvc.perform(get("/review")).andExpect(content().string(containsString("今日 " + todayBefore + " 問のうち")));
            // 終わりの画面で、正しく解き直せた数を言い、その × に仮縫いの破線を重ね、行に「仮縫いできた」
            mvc.perform(get("/review").param("retry", "99").param("r", "1"))
                    .andExpect(content().string(containsString("仮縫いできた。本縫いは明日の復習で")))
                    .andExpect(content().string(containsString("miss basted")))
                    .andExpect(content().string(containsString("仮縫いできた")));
            // 1 問も解けなかったときは、仮縫いの札を付けずに「まだ」と言う
            mvc.perform(get("/review").param("retry", "99").param("r", "0"))
                    .andExpect(content().string(containsString("は、まだ。明日の復習で、もう一度")));
            // 解き直しの状態はセッションに残る(本文へ行って戻っても、仮縫いの跡が消えない)。まだの問題は、もう一度回せる
            var session = new org.springframework.mock.web.MockHttpSession();
            mvc.perform(get("/review").param("retry", "0").session(session)).andExpect(content().string(containsString("解き直し <b>1</b>")));
            int total = Integer.parseInt(mvc.perform(get("/review").param("retry", "0").session(session)).andReturn().getResponse()
                    .getContentAsString().replaceAll("(?s).*解き直し <b>1</b> / (\\d+).*", "$1"));
            for (int k = 0; k < total; k++) {
                String page = mvc.perform(get("/review").param("retry", String.valueOf(k)).session(session)).andReturn().getResponse().getContentAsString();
                String id = page.replaceAll("(?s).*hx-post=\"/review/(\\d+)/quiz.*", "$1");
                mvc.perform(post("/review/" + id + "/quiz").param("choice", "-1").param("retry", String.valueOf(k))
                        .param("total", String.valueOf(total)).session(session)).andExpect(status().isOk());
            }
            mvc.perform(get("/review").session(session))
                    .andExpect(content().string(containsString("は、まだ。明日の復習で、もう一度")))
                    .andExpect(content().string(containsString("を、もう一度")))
                    .andExpect(content().string(containsString("missed-again")))
                    // 「もう一度」は押した画面の回を持つ(戻る・再読み込みで、次の回へ勝手に進まない)
                    .andExpect(content().string(containsString("still=1")));
            mvc.perform(get("/review").param("retry", "0").param("still", "1").session(session))
                    .andExpect(content().string(containsString("解き直し <b>1</b>")));
            // 2 回目の途中で同じ URL をもう一度開いても、3 回目にはならない(2 回目の続きのまま)
            mvc.perform(get("/review").param("retry", "0").param("still", "1").session(session))
                    .andExpect(content().string(containsString("解き直し <b>1</b>")));
            mvc.perform(get("/review").session(session))
                    .andExpect(content().string(containsString("解き直しの続きへ")));
        } finally {
            var all = reviewItems.findAll();
            all.forEach(it -> { if (saved.containsKey(it.getId())) it.setDueDate(saved.get(it.getId())); });
            reviewItems.saveAll(all);
        }
    }
}
