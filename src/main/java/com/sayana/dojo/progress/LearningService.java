package com.sayana.dojo.progress;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.ExerciseDef;
import com.sayana.dojo.content.Lesson;
import com.sayana.dojo.exec.GradeResult;
import com.sayana.dojo.exec.Grader;

/**
 * 学習の「書き込み」側:提出・採点、クイズ、チェックリスト、足あと。
 * 結果は復習キューと学習日にも反映する。
 */
@Service
public class LearningService {

    private final ContentRepository content;
    private final Grader grader;
    private final SubmissionRepository submissions;
    private final QuizAttemptRepository quizAttempts;
    private final ChecklistCheckRepository checklist;
    private final LessonProgressRepository progress;
    private final ReviewService review;
    private final ActivityService activity;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate tx;

    public LearningService(ContentRepository content, Grader grader, SubmissionRepository submissions,
                           QuizAttemptRepository quizAttempts, ChecklistCheckRepository checklist,
                           LessonProgressRepository progress, ReviewService review, ActivityService activity,
                           ObjectMapper json, Clock clock, PlatformTransactionManager txManager) {
        this.content = content;
        this.grader = grader;
        this.submissions = submissions;
        this.quizAttempts = quizAttempts;
        this.checklist = checklist;
        this.progress = progress;
        this.review = review;
        this.activity = activity;
        this.json = json;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 提出結果。 */
    public record SubmitOutcome(Submission submission, GradeResult result, ReviewItem reviewItem) {
    }

    /**
     * 採点して記録する。採点(別JVM)はトランザクションの外で行う。
     *
     * @param fromReview 復習キューからの提出か(そうなら復習の段階を進める/戻す)
     */
    public SubmitOutcome submit(ExerciseDef exercise, String code, Long reviewItemId) {
        GradeResult result = grader.grade(exercise, code);
        return tx.execute(status -> record(exercise, code, result, reviewItemId));
    }

    private SubmitOutcome record(ExerciseDef exercise, String code, GradeResult result, Long reviewItemId) {
        if (result.status() == GradeResult.Status.ERROR && result.tests().isEmpty()
                && result.message() != null && result.message().contains("実行中です")) {
            // 混雑で採点できなかっただけなら記録しない
            return new SubmitOutcome(null, result, null);
        }
        Submission s = submissions.save(new Submission(exercise.key(), exercise.lessonId(), exercise.title(), code,
                result.status().name(), (int) result.passedCount(), result.totalCount(), toJson(result),
                reviewItemId != null, Instant.now(clock)));
        ReviewItem item = reviewItemId != null
                ? review.recordReview(reviewItemId, result.passed())
                : review.recordPractice(ReviewItem.Type.EXERCISE, exercise.key(), exercise.lessonId(), result.passed());
        activity.record();
        touch(exercise.lessonId());
        return new SubmitOutcome(s, result, item);
    }

    /** クイズ1問ごとの採点結果。 */
    public record QuizOutcome(QuizAttempt attempt, List<Integer> answers, List<Boolean> correct) {
    }

    /** 確認クイズをまとめて採点する。answers は問題番号→選んだ選択肢。 */
    @Transactional
    public QuizOutcome submitQuiz(Lesson lesson, Map<Integer, Integer> answers) {
        List<Integer> chosen = new ArrayList<>();
        List<Boolean> correct = new ArrayList<>();
        int score = 0;
        for (int i = 0; i < lesson.quiz().size(); i++) {
            int a = answers.getOrDefault(i, -1);
            boolean ok = a == lesson.quiz().get(i).answer();
            chosen.add(a);
            correct.add(ok);
            if (ok) {
                score++;
            }
            if (a >= 0) {
                review.recordPractice(ReviewItem.Type.QUIZ, lesson.id() + "#" + i, lesson.id(), ok);
            }
        }
        QuizAttempt attempt = quizAttempts.save(new QuizAttempt(lesson.id(), score, lesson.quiz().size(),
                chosen.stream().map(String::valueOf).collect(Collectors.joining(",")), Instant.now(clock)));
        activity.record();
        touch(lesson.id());
        return new QuizOutcome(attempt, chosen, correct);
    }

    /** 復習キューでのクイズ1問。 */
    @Transactional
    public boolean answerReviewQuiz(ReviewItem item, int choice) {
        Lesson lesson = content.lesson(item.getLessonId()).orElseThrow();
        boolean ok = lesson.quiz().get(item.quizIndex()).answer() == choice;
        review.recordReview(item.getId(), ok);
        activity.record();
        return ok;
    }

    /** チェックリストの ON/OFF を切り替え、切り替え後の状態を返す。 */
    @Transactional
    public boolean toggleChecklist(String lessonId, String key) {
        var existing = checklist.findByLessonIdAndItemKey(lessonId, key);
        if (existing.isPresent()) {
            checklist.delete(existing.get());
            return false;
        }
        checklist.save(new ChecklistCheck(lessonId, key, Instant.now(clock)));
        activity.record();
        touch(lessonId);
        return true;
    }

    @Transactional
    public void visit(String lessonId) {
        LessonProgress p = progress.findById(lessonId).orElseGet(() -> progress.save(new LessonProgress(lessonId)));
        Instant now = Instant.now(clock);
        if (p.getFirstVisitedAt() == null) {
            p.setFirstVisitedAt(now);
        }
        p.setLastVisitedAt(now);
    }

    @Transactional
    public void markRead(String lessonId) {
        LessonProgress p = progress.findById(lessonId).orElseGet(() -> progress.save(new LessonProgress(lessonId)));
        if (p.getReadAt() == null) {
            p.setReadAt(Instant.now(clock));
        }
        activity.record();
        touch(lessonId);
    }

    @Transactional
    public void markSampleRun(String lessonId) {
        LessonProgress p = progress.findById(lessonId).orElseGet(() -> progress.save(new LessonProgress(lessonId)));
        if (p.getSampleRunAt() == null) {
            p.setSampleRunAt(Instant.now(clock));
        }
        activity.record();
    }

    private void touch(String lessonId) {
        LessonProgress p = progress.findById(lessonId).orElseGet(() -> progress.save(new LessonProgress(lessonId)));
        if (p.getFirstVisitedAt() == null) {
            p.setFirstVisitedAt(Instant.now(clock));
        }
    }

    public GradeResult readResult(Submission s) {
        try {
            return json.readValue(s.getResultJson(), GradeResult.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return new GradeResult(GradeResult.Status.ERROR, List.of(), List.of(), "採点結果を読み込めませんでした", "", 0);
        }
    }

    private String toJson(GradeResult r) {
        try {
            return json.writeValueAsString(r);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
