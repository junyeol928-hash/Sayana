package com.sayana.dojo.progress;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sayana.dojo.content.Chapter;
import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.ExerciseDef;
import com.sayana.dojo.content.Lesson;
import com.sayana.dojo.content.Part;

/**
 * 学習の「読み取り」側:レッスンの状態、部・章ごとの進捗、苦手レッスン。
 * 個人用でデータ量が小さいので、毎回まとめて読み込んで計算する。
 */
@Service
@Transactional(readOnly = true)
public class ProgressService {

    /** クイズの合格ライン(8割)。 */
    public static final double QUIZ_PASS_RATE = 0.8;

    private final ContentRepository content;
    private final SubmissionRepository submissions;
    private final QuizAttemptRepository quizAttempts;
    private final ChecklistCheckRepository checklist;
    private final LessonProgressRepository progress;
    private final ReviewItemRepository reviewItems;

    public ProgressService(ContentRepository content, SubmissionRepository submissions,
                           QuizAttemptRepository quizAttempts, ChecklistCheckRepository checklist,
                           LessonProgressRepository progress, ReviewItemRepository reviewItems) {
        this.content = content;
        this.submissions = submissions;
        this.quizAttempts = quizAttempts;
        this.checklist = checklist;
        this.progress = progress;
        this.reviewItems = reviewItems;
    }

    public enum State {
        PLANNED("準備中"), NOT_STARTED("未着手"), IN_PROGRESS("学習中"), COMPLETED("完了");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** 1レッスンの状態。 */
    public record LessonStatus(Lesson lesson, State state, boolean read, boolean sampleRun, int exercisesPassed,
                               int exercisesTotal, int aiReviews, Integer bestQuiz, Integer lastQuiz, int quizTotal,
                               int checklistDone, int checklistTotal, Set<String> passedExercises,
                               Set<String> checkedItems) {

        public boolean completed() {
            return state == State.COMPLETED;
        }

        public boolean exercisesDone() {
            return exercisesPassed >= exercisesTotal;
        }

        public boolean quizPassed() {
            return quizTotal == 0 || (bestQuiz != null && bestQuiz >= quizPassScore(quizTotal));
        }

        public boolean checklistComplete() {
            return checklistDone >= checklistTotal;
        }

        public int quizPassScore() {
            return quizPassScore(quizTotal);
        }

        private static int quizPassScore(int total) {
            return (int) Math.ceil(total * QUIZ_PASS_RATE);
        }
    }

    public record ChapterProgress(Chapter chapter, List<LessonStatus> lessons) {

        public long completed() {
            return lessons.stream().filter(LessonStatus::completed).count();
        }

        public long ready() {
            return lessons.stream().filter(l -> l.lesson().ready()).count();
        }

        public int percent() {
            return lessons.isEmpty() ? 0 : (int) (completed() * 100 / lessons.size());
        }
    }

    public record PartProgress(Part part, List<ChapterProgress> chapters) {

        public long completed() {
            return chapters.stream().mapToLong(ChapterProgress::completed).sum();
        }

        public long total() {
            return chapters.stream().mapToLong(c -> c.lessons().size()).sum();
        }

        public long ready() {
            return chapters.stream().mapToLong(ChapterProgress::ready).sum();
        }

        public int percent() {
            return total() == 0 ? 0 : (int) (completed() * 100 / total());
        }
    }

    /** 苦手レッスン。 */
    public record WeakLesson(Lesson lesson, double score, List<String> reasons) {
    }

    public LessonStatus status(Lesson lesson) {
        return new Snapshot().status(lesson);
    }

    public List<PartProgress> overview() {
        Snapshot snap = new Snapshot();
        List<PartProgress> parts = new ArrayList<>();
        for (Part p : content.parts()) {
            List<ChapterProgress> chapters = p.chapters().stream()
                    .map(c -> new ChapterProgress(c, c.lessons().stream().map(snap::status).toList()))
                    .toList();
            parts.add(new PartProgress(p, chapters));
        }
        return parts;
    }

    /** 次にやるとよいレッスン(学習中のもの → 未着手の最初のもの)。 */
    public Optional<Lesson> nextLesson() {
        Snapshot snap = new Snapshot();
        List<LessonStatus> ready = content.readyLessons().stream().map(snap::status).toList();
        return ready.stream().filter(s -> s.state() == State.IN_PROGRESS).map(LessonStatus::lesson).findFirst()
                .or(() -> ready.stream().filter(s -> s.state() == State.NOT_STARTED).map(LessonStatus::lesson)
                        .findFirst());
    }

    /**
     * 苦手レッスン。直近クイズの誤答率・演習の不合格回数・復習でのつまずき回数から点数をつける。
     */
    public List<WeakLesson> weakLessons(int limit) {
        Map<String, List<Submission>> subsByLesson = submissions.findAll().stream()
                .collect(Collectors.groupingBy(Submission::getLessonId));
        Map<String, Integer> lapses = reviewItems.findAll().stream()
                .collect(Collectors.groupingBy(ReviewItem::getLessonId, Collectors.summingInt(ReviewItem::getLapses)));
        List<WeakLesson> result = new ArrayList<>();
        for (Lesson lesson : content.readyLessons()) {
            List<String> reasons = new ArrayList<>();
            double score = 0;
            var last = quizAttempts.findFirstByLessonIdOrderByCreatedAtDesc(lesson.id());
            if (last.isPresent() && last.get().getTotal() > 0) {
                QuizAttempt a = last.get();
                double wrongRate = (double) (a.getTotal() - a.getScore()) / a.getTotal();
                score += wrongRate * 3;
                if (a.getScore() < a.getTotal()) {
                    reasons.add("直近のクイズ " + a.getScore() + "/" + a.getTotal());
                }
            }
            long failed = subsByLesson.getOrDefault(lesson.id(), List.of()).stream()
                    .filter(s -> !s.isPassed()).count();
            if (failed > 0) {
                score += Math.min(failed, 6) * 0.3;
                reasons.add("演習の不合格 " + failed + "回");
            }
            int lapse = lapses.getOrDefault(lesson.id(), 0);
            if (lapse > 0) {
                score += lapse * 0.5;
                reasons.add("つまずき " + lapse + "回");
            }
            if (score >= 1.0) {
                result.add(new WeakLesson(lesson, score, reasons));
            }
        }
        result.sort(Comparator.comparingDouble(WeakLesson::score).reversed());
        return result.stream().limit(limit).toList();
    }

    /** 計算用にまとめて読み込んだデータ。 */
    private final class Snapshot {
        private final Map<String, LessonProgress> progressMap = progress.findAll().stream()
                .collect(Collectors.toMap(LessonProgress::getLessonId, Function.identity()));
        private final Set<String> passed = new HashSet<>();
        private final Map<String, Integer> reviewsByLesson = new HashMap<>();
        private final Set<String> touchedLessons = new HashSet<>();
        private final Map<String, List<QuizAttempt>> quizByLesson = quizAttempts.findAll().stream()
                .collect(Collectors.groupingBy(QuizAttempt::getLessonId));
        private final Map<String, Set<String>> checkedByLesson = checklist.findAll().stream()
                .collect(Collectors.groupingBy(ChecklistCheck::getLessonId,
                        Collectors.mapping(ChecklistCheck::getItemKey, Collectors.toSet())));

        Snapshot() {
            for (Submission s : submissions.findAll()) {
                touchedLessons.add(s.getLessonId());
                if (s.isPassed()) {
                    passed.add(s.getExerciseKey());
                }
                if (s.getAiReview() != null) {
                    reviewsByLesson.merge(s.getLessonId(), 1, Integer::sum);
                }
            }
        }

        LessonStatus status(Lesson lesson) {
            if (!lesson.ready()) {
                return new LessonStatus(lesson, State.PLANNED, false, false, 0, 0, 0, null, null, 0, 0, 0, Set.of(),
                        Set.of());
            }
            LessonProgress p = progressMap.get(lesson.id());
            Set<String> passedHere = lesson.exercises().stream().map(ExerciseDef::key).filter(passed::contains)
                    .collect(Collectors.toSet());
            List<QuizAttempt> attempts = quizByLesson.getOrDefault(lesson.id(), List.of());
            Integer best = attempts.stream().map(QuizAttempt::getScore).max(Integer::compare).orElse(null);
            Integer lastScore = attempts.stream().max(Comparator.comparing(QuizAttempt::getCreatedAt))
                    .map(QuizAttempt::getScore).orElse(null);
            Set<String> checked = checkedByLesson.getOrDefault(lesson.id(), Set.of());
            Set<String> validKeys = lesson.checklist().stream().map(Lesson.ChecklistItem::key)
                    .collect(Collectors.toSet());
            int checkedCount = (int) checked.stream().filter(validKeys::contains).count();
            boolean read = p != null && p.getReadAt() != null;
            boolean sampleRun = p != null && p.getSampleRunAt() != null;

            LessonStatus tmp = new LessonStatus(lesson, State.NOT_STARTED, read, sampleRun, passedHere.size(),
                    lesson.exercises().size(), reviewsByLesson.getOrDefault(lesson.id(), 0), best, lastScore,
                    lesson.quiz().size(), checkedCount, lesson.checklist().size(), passedHere, checked);
            State state;
            if (tmp.exercisesDone() && tmp.quizPassed() && tmp.checklistComplete()
                    && (best != null || lesson.quiz().isEmpty())) {
                state = State.COMPLETED;
            } else if (p != null || touchedLessons.contains(lesson.id()) || !attempts.isEmpty()
                    || !checked.isEmpty()) {
                state = State.IN_PROGRESS;
            } else {
                state = State.NOT_STARTED;
            }
            return new LessonStatus(lesson, state, read, sampleRun, tmp.exercisesPassed(), tmp.exercisesTotal(),
                    tmp.aiReviews(), best, lastScore, tmp.quizTotal(), checkedCount, tmp.checklistTotal(), passedHere,
                    checked);
        }
    }
}
