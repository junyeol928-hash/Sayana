package com.sayana.dojo.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("第1部 Java基礎")));
        mvc.perform(get("/lessons/java-hello")).andExpect(status().isOk())
                .andExpect(content().string(containsString("STSでやってみよう")))
                .andExpect(content().string(containsString("AI機能はオフです")));
        mvc.perform(get("/lessons/spring-di")).andExpect(status().isOk())
                .andExpect(content().string(containsString("準備中")));
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
        mvc.perform(post("/lessons/java-loops/read")).andExpect(status().isOk());
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
                .andExpect(status().isOk()).andExpect(content().string(containsString("3日後に復習")));

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
                .andExpect(status().isOk()).andExpect(content().string(containsString("✅")));
        mvc.perform(post("/lessons/java-hello/checklist").param("key", key))
                .andExpect(status().isOk()).andExpect(content().string(containsString("⬜")));
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
}
