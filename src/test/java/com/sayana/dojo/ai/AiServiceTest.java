package com.sayana.dojo.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sayana.dojo.ai.LlmClient.LlmRequest;
import com.sayana.dojo.ai.LlmClient.LlmResponse;
import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.ExerciseDef;
import com.sayana.dojo.progress.ChatMessageRepository;
import com.sayana.dojo.progress.GeneratedExercise;
import com.sayana.dojo.progress.HintRecord;
import com.sayana.dojo.progress.LearningService;
import com.sayana.dojo.progress.Submission;
import com.sayana.dojo.support.TestClockConfig;

/** Claude API はモックに差し替える(実APIには通信しない)。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class AiServiceTest {

    @MockitoBean
    LlmClient llm;

    @Autowired
    AiService ai;
    @Autowired
    ContentRepository content;
    @Autowired
    LearningService learning;
    @Autowired
    ChatMessageRepository chat;
    @Autowired
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(llm.isAvailable()).thenReturn(true);
    }

    private static LlmResponse reply(String text) {
        return new LlmResponse(text, "end_turn", 100, 50, 0, 0);
    }

    private LlmRequest lastRequest() {
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm, atLeastOnce()).complete(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("チューター: レッスン本文はキャッシュ対象の system に、コードと質問は messages に入る")
    void tutorPromptLayout() {
        when(llm.complete(any())).thenReturn(reply("いい質問だね。int 同士だと…"));
        var saved = ai.askTutor("java-variables", "なぜ 7 / 2 は 3 なの?", "int x = 7 / 2;");

        LlmRequest req = lastRequest();
        assertThat(req.model()).isEqualTo("claude-sonnet-5-5");
        assertThat(req.system()).hasSize(2);
        assertThat(req.system().get(0).cache()).isTrue();
        assertThat(req.system().get(0).text())
                .contains(content.lesson("java-variables").orElseThrow().bodyMarkdown().strip().substring(0, 50))
                .doesNotContain("int x = 7 / 2;");
        assertThat(req.system().get(1).cache()).isFalse();
        assertThat(req.system().get(1).text()).contains("答えを丸ごと教えない");
        assertThat(req.messages().get(req.messages().size() - 1).text())
                .contains("int x = 7 / 2;").contains("なぜ 7 / 2 は 3 なの?");
        assertThat(saved).extracting(m -> m.getRole()).containsExactly("user", "assistant");
        assertThat(chat.findByLessonIdOrderByIdAsc("java-variables")).hasSize(2);
    }

    @Test
    @DisplayName("同じレッスンなら、キャッシュ対象の system は毎回まったく同じ文字列になる")
    void cachedPrefixIsStable() {
        when(llm.complete(any())).thenReturn(reply("ok"));
        ai.askTutor("java-loops", "質問1", null);
        ai.askTutor("java-loops", "質問2", "for (;;) {}");
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm, times(2)).complete(captor.capture());
        assertThat(captor.getAllValues().get(0).system().get(0).text())
                .isEqualTo(captor.getAllValues().get(1).system().get(0).text());
        // 2回目は前の会話も渡す
        assertThat(captor.getAllValues().get(1).messages()).hasSize(3);
    }

    @Test
    @DisplayName("段階ヒント: 押すたびに 方向性 → 手がかり → ほぼ答え、4回目は出ない")
    void hintsEscalate() {
        when(llm.complete(any())).thenReturn(reply("ヒントA"), reply("ヒントB"), reply("ヒントC"));
        String key = "java-if/ex1";
        HintRecord h1 = ai.nextHint(key, null);
        HintRecord h2 = ai.nextHint(key, "// 途中");
        HintRecord h3 = ai.nextHint(key, "// もう少し");
        assertThat(List.of(h1.getLevel(), h2.getLevel(), h3.getLevel())).containsExactly(1, 2, 3);
        LlmRequest req = lastRequest();
        assertThat(req.model()).as("段階ヒントは Sonnet").isEqualTo("claude-sonnet-5-5");
        assertThat(req.messages().get(0).text()).contains("レベル3").contains("ヒントA").contains("ヒントB");
        assertThatThrownBy(() -> ai.nextHint(key, null)).isInstanceOf(AiException.class);
        ai.resetHints(key);
        assertThat(ai.hintsFor(key)).isEmpty();
    }

    @Test
    @DisplayName("コードレビュー: 採点結果と模範解答を渡し、講評を提出に保存する")
    void reviewIsSaved() {
        when(llm.complete(any())).thenReturn(reply("### 総評\nいいね"));
        ExerciseDef ex = content.exercise("java-hello/ex1").orElseThrow();
        Submission s = learning.submit(ex, ex.solution(), null).submission();
        Submission reviewed = ai.review(s.getId());
        assertThat(reviewed.getAiReview()).contains("総評");
        LlmRequest req = lastRequest();
        assertThat(req.model()).as("コードレビューは Opus").isEqualTo("claude-opus-5-5");
        assertThat(req.messages().get(0).text()).contains("合格").contains(ex.solution().strip().substring(0, 20));
    }

    @Test
    @DisplayName("エラー翻訳: コンパイルエラーの内容を渡して解説を保存する")
    void explainCompileError() {
        when(llm.complete(any())).thenReturn(reply("### ひとことで言うと\nセミコロン忘れ"));
        ExerciseDef ex = content.exercise("java-hello/ex1").orElseThrow();
        Submission s = learning.submit(ex, "public class MorningGreeting { int x = 1 }", null).submission();
        assertThat(s.isPassed()).isFalse();
        Submission explained = ai.explainSubmission(s.getId());
        assertThat(explained.getErrorExplanation()).contains("セミコロン");
        assertThat(lastRequest().messages().get(0).text()).contains("コンパイルエラー");
    }

    private static final String GOOD = """
            <title>在庫の合計</title>
            <className>StockTotal</className>
            <difficulty>1</difficulty>
            <problem>配列の合計を返す `static int total(int[] a)` を作る。</problem>
            <starter>
            public class StockTotal {
                public static int total(int[] a) {
                    // TODO: 合計を返す
                    return 0;
                }
            }
            </starter>
            <solution>
            public class StockTotal {
                public static int total(int[] a) {
                    int sum = 0;
                    for (int x : a) sum += x;
                    return sum;
                }
            }
            </solution>
            <test>
            import static org.junit.jupiter.api.Assertions.*;
            import org.junit.jupiter.api.*;

            class StockTotalTest {
                @Test
                @DisplayName("合計できる")
                void sum() { assertEquals(6, StockTotal.total(new int[] {1, 2, 3}), "1+2+3 は 6"); }

                @Test
                @DisplayName("空なら0")
                void empty() { assertEquals(0, StockTotal.total(new int[0])); }
            }
            </test>
            """;

    @Test
    @DisplayName("類題生成: 模範解答が採点テストに合格したものだけ保存する(ダメなら作り直す)")
    void generateValidatesWithGrader() {
        String broken = GOOD.replace("sum += x;", "sum -= x;");
        when(llm.complete(any())).thenReturn(reply(broken), reply(GOOD));
        GeneratedExercise g = ai.generateExercise("java-arrays");
        assertThat(g.getClassName()).isEqualTo("StockTotal");
        assertThat(g.getSolution()).contains("sum += x;");
        verify(llm, times(2)).complete(any());
        assertThat(lastRequest().messages()).hasSize(3);
        assertThat(lastRequest().messages().get(2).text()).contains("検証に失敗しました");
    }

    @Test
    @DisplayName("類題生成: 2回とも検証に失敗したらエラーにする")
    void generateGivesUp() {
        when(llm.complete(any())).thenReturn(reply("形式が崩れた出力"));
        assertThatThrownBy(() -> ai.generateExercise("java-arrays")).isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("タグ形式の出力をパースできる(```で囲まれていても可)")
    void parseDraft() {
        var d = AiService.parse(GOOD.replace("<solution>\n", "<solution>\n```java\n").replace("\n</solution>", "\n```\n</solution>"));
        assertThat(d).isPresent();
        assertThat(d.get().solution()).startsWith("public class StockTotal").doesNotContain("```");
        assertThat(AiService.parse("<title>x</title>")).isEmpty();
    }

    @Test
    @DisplayName("画面: AIの回答はMarkdownとして描画し、HTMLはエスケープする")
    void aiFragmentsRenderMarkdownSafely() throws Exception {
        when(llm.complete(any())).thenReturn(reply("### 見出し\n**太字** <script>alert(1)</script>"));
        mvc.perform(post("/ai/chat").param("lessonId", "java-operators").param("question", "質問です")
                        .param("code", "int a = 1;"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<strong>太字</strong>")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))));
        mvc.perform(post("/exercises/hint").param("key", "java-operators/ex1").param("code", "// まだ"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ヒント1:方向性")))
                .andExpect(content().string(containsString("<h3>見出し</h3>")));
        ExerciseDef ex = content.exercise("java-operators/ex1").orElseThrow();
        Submission s = learning.submit(ex, ex.solution(), null).submission();
        mvc.perform(post("/ai/review/" + s.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AIコードレビュー")))
                .andExpect(content().string(containsString("<strong>太字</strong>")));
    }
}
