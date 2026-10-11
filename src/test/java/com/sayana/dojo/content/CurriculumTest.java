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

/**
 * 教材そのもののテスト。
 * <ul>
 *   <li>全演習の模範解答が採点テストに合格する</li>
 *   <li>全演習の雛形はコンパイルでき、採点テストには落ちる(テストが甘くない)</li>
 *   <li>全サンプルが JShell で最後まで動く</li>
 *   <li>公開レッスンに必要な要素(解説・サンプル・演習・クイズ5問・コラム)がそろっている</li>
 * </ul>
 */
class CurriculumTest {

    private static final DojoProperties PROPS = new DojoProperties();
    private static final ContentRepository CONTENT = new ContentRepository(PROPS, new MarkdownRenderer());
    private static final Grader GRADER = new Grader(PROPS, new ExecutionGate(PROPS));
    private static final JShellRunner RUNNER = new JShellRunner(PROPS, new ExecutionGate(PROPS));

    static Stream<Arguments> exercises() {
        return CONTENT.allExercises().stream().map(e -> Arguments.of(e.key(), e));
    }

    static Stream<Arguments> samples() {
        return CONTENT.readyLessons().stream().flatMap(l -> l.samples().stream()
                .map(s -> Arguments.of(l.id() + " / " + s.title(), s)));
    }

    @Test
    @DisplayName("4部構成・全50レッスンがすべて公開されている")
    void curriculumShape() {
        assertThat(CONTENT.parts()).hasSize(4);
        assertThat(CONTENT.allLessons()).hasSize(50);
        assertThat(CONTENT.allLessons()).allSatisfy(l -> assertThat(l.ready()).as(l.id()).isTrue());
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
    }
}
