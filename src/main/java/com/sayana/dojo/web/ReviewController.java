package com.sayana.dojo.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.Lesson;
import com.sayana.dojo.progress.ExerciseCatalog;
import com.sayana.dojo.progress.LearningService;
import com.sayana.dojo.progress.ReviewItem;
import com.sayana.dojo.progress.ReviewItemRepository;
import com.sayana.dojo.progress.ReviewService;

/** 復習キュー。期限が来たものを1問ずつ出す。 */
@Controller
public class ReviewController {

    private final ReviewService review;
    private final ReviewItemRepository items;
    private final ContentRepository content;
    private final ExerciseCatalog catalog;
    private final ExerciseViews views;
    private final LearningService learning;

    public ReviewController(ReviewService review, ReviewItemRepository items, ContentRepository content,
                            ExerciseCatalog catalog, ExerciseViews views, LearningService learning) {
        this.review = review;
        this.items = items;
        this.content = content;
        this.catalog = catalog;
        this.views = views;
        this.learning = learning;
    }

    @GetMapping("/review")
    public String queue(@RequestParam(name = "go", defaultValue = "-1") int goOnAt,
                        @RequestParam(name = "retry", defaultValue = "-1") int retryAt, Model model) {
        List<ReviewItem> due = review.due().stream().filter(this::stillExists).toList();
        model.addAttribute("due", due);
        List<ReviewItem> upcoming = review.upcoming().stream().filter(this::stillExists).limit(30).toList();
        model.addAttribute("upcomingItems", upcoming);
        model.addAttribute("graduated", review.graduatedCount());
        Map<String, String> titles = LessonTitles.of(content);
        model.addAttribute("lessonTitles", titles);
        model.addAttribute("upcomingRows", upcomingRows(upcoming, titles));
        int[] tally = review.todayTally();
        model.addAttribute("todayCount", tally[0]);
        model.addAttribute("todayCorrect", tally[1]);
        // 10 問ごとに区切りを出す(残りが多い日でも、ここで止めてよいと分かるように)。「続ける」で ?go=今日の数
        List<ReviewItem> today = review.todayItems();
        if (tally[0] > 0 && tally[0] % 10 == 0 && goOnAt != tally[0] && !due.isEmpty()) {
            List<ReviewItem> block = today.subList(Math.max(0, today.size() - 10), today.size());
            List<Boolean> results = block.stream().map(it -> Boolean.TRUE.equals(it.getLastCorrect())).toList();
            model.addAttribute("breakHere", true);
            model.addAttribute("breakBlock", results);
            model.addAttribute("breakMiss", results.stream().filter(ok -> !ok).count());
            model.addAttribute("missed", missed(block));
            return "review";
        }
        if (due.isEmpty()) {
            // 今日の終わり: 区切りと同じ部品で、今日答えた全問を運針に(10 目で 1 行)、間違えた問題と読み直す所
            List<Boolean> results = today.stream().map(it -> Boolean.TRUE.equals(it.getLastCorrect())).toList();
            java.util.List<List<Boolean>> rows = new java.util.ArrayList<>();
            for (int i = 0; i < results.size(); i += 10) {
                rows.add(results.subList(i, Math.min(results.size(), i + 10)));
            }
            model.addAttribute("doneRows", rows);
            model.addAttribute("missed", missed(today));
            // 解き直し: 今日まちがえたクイズを、その日のうちにもう一度(記録せず、明日の予定も変えない。スマホ版と同じ)。
            // ?retry=n で n 問目。並びは今日答えた順で、解き直しでは記録が変わらないので番号はずれない
            List<ReviewItem> again = today.stream().filter(it -> !Boolean.TRUE.equals(it.getLastCorrect()))
                    .filter(it -> it.getItemType() == ReviewItem.Type.QUIZ).filter(this::stillExists).toList();
            model.addAttribute("retryCount", again.size());
            model.addAttribute("retryDone", retryAt >= again.size() && retryAt > 0);
            if (retryAt >= 0 && retryAt < again.size()) {
                ReviewItem item = again.get(retryAt);
                Lesson.QuizQuestion q = content.lesson(item.getLessonId()).orElseThrow().quiz().get(item.quizIndex());
                model.addAttribute("item", item);
                model.addAttribute("retryAt", retryAt);
                model.addAttribute("question", q);
                model.addAttribute("choiceOrder", review.choiceOrder(item, q.choicesHtml().size()));
                return "review";
            }
        }
        review.next(due).ifPresent(item -> {
            model.addAttribute("item", item);
            Lesson lesson = content.lesson(item.getLessonId()).orElse(null);
            model.addAttribute("itemLesson", lesson);
            if (item.getItemType() == ReviewItem.Type.QUIZ) {
                Lesson.QuizQuestion q = lesson.quiz().get(item.quizIndex());
                model.addAttribute("question", q);
                model.addAttribute("choiceOrder", review.choiceOrder(item, q.choicesHtml().size()));
            } else {
                model.addAttribute("view", views.forReview(catalog.find(item.getItemRef()).orElseThrow(), item.getId()));
            }
        });
        return "review";
    }

    @PostMapping("/review/{id}/quiz")
    public String answerQuiz(@PathVariable long id, @RequestParam(defaultValue = "-1") int choice,
                             @RequestParam(defaultValue = "-1") int retry, @RequestParam(defaultValue = "0") int total, Model model) {
        ReviewItem item = items.findById(id).orElseThrow();
        Lesson lesson = content.lesson(item.getLessonId()).orElseThrow();
        if (retry >= 0) {
            // 解き直しは採点だけ(記録・予定・今日の数は変えない)
            Lesson.QuizQuestion q = lesson.quiz().get(item.quizIndex());
            boolean ok = q.answer() == choice;
            model.addAttribute("correct", ok);
            model.addAttribute("choice", choice);
            model.addAttribute("question", q);
            model.addAttribute("lesson", lesson);
            model.addAttribute("retryNext", retry + 1);
            model.addAttribute("retryLast", retry + 1 >= total);
            model.addAttribute("reviewLabel", ok ? "解き直せた。明日もう一度出ます" : "明日もう一度出ます");
            return "fragments/review-parts :: quizAnswer";
        }
        boolean correct = learning.answerReviewQuiz(item, choice);
        ReviewItem updated = items.findById(id).orElseThrow();
        model.addAttribute("correct", correct);
        model.addAttribute("choice", choice);
        model.addAttribute("question", lesson.quiz().get(item.quizIndex()));
        model.addAttribute("reviewLabel", ReviewService.nextLabel(updated));
        model.addAttribute("graduatedNow", updated.isGraduated());
        model.addAttribute("nextDays", ReviewService.INTERVAL_DAYS[Math.min(updated.getStage(), ReviewService.INTERVAL_DAYS.length - 1)]);
        model.addAttribute("nextStep", Math.min(updated.getStage(), ReviewService.INTERVAL_DAYS.length - 1) + 1);
        model.addAttribute("lesson", lesson);
        // 上の「残り n 問・今日 n / 10」も答えた分だけ進める(hx-swap-oob で差し替える)
        model.addAttribute("dueLeft", review.due().stream().filter(this::stillExists).count());
        model.addAttribute("todayCount", review.todayTally()[0]);
        return "fragments/review-parts :: quizAnswer";
    }

    /**
     * 間違えた問題の行。レッスン名と、どう読み違えたか(選んだ誤答の why の 1 文目。why が無い演習や、選んだ答えの記録が無い分は
     * 問いの書き出し)、読み直す先(その問いの段)。3 件まで(ほかは数だけ画面に出す)。
     */
    public record Missed(String lessonId, String lessonTitle, String text, boolean why, String anchor) {
    }

    private List<Missed> missed(List<ReviewItem> answered) {
        return answered.stream().filter(it -> !Boolean.TRUE.equals(it.getLastCorrect())).filter(this::stillExists).map(it -> {
            Lesson lesson = content.lesson(it.getLessonId()).orElseThrow();
            if (it.getItemType() != ReviewItem.Type.QUIZ) {
                String title = catalog.find(it.getItemRef()).map(e -> e.title()).orElse("");
                return new Missed(lesson.id(), lesson.title(), clip(title, 30), false, "#write");
            }
            Lesson.QuizQuestion q = lesson.quiz().get(it.quizIndex());
            Integer c = it.getLastChoice();
            if (c != null && c >= 0 && c < q.whyHtml().size() && !q.whyHtml().get(c).isBlank()) {
                String why = HtmlUtils.htmlUnescape(q.whyHtml().get(c).replaceAll("<[^>]+>", ""));
                return new Missed(lesson.id(), lesson.title(), clip(why.split("。")[0], 44), true, "#quiz");
            }
            String text = q.questionMarkdown().replaceAll("(?s)```.*?```", " ").replace("`", "").replace("**", "");
            return new Missed(lesson.id(), lesson.title(), clip(text, 30), false, "#quiz");
        }).toList();
    }

    /** この先の予定の 1 行。同じ日の同じレッスンは 1 行にまとめ、段階の運針を問題の数だけ並べる(同じ行が 4 つ並んでいた)。 */
    public record UpcomingRow(LocalDate dueDate, String lessonTitle, List<Integer> stages, long exercises) {
    }

    static List<UpcomingRow> upcomingRows(List<ReviewItem> upcoming, Map<String, String> titles) {
        Map<String, List<ReviewItem>> groups = new LinkedHashMap<>();
        for (ReviewItem it : upcoming) {
            groups.computeIfAbsent(it.getDueDate() + "|" + it.getLessonId(), k -> new ArrayList<>()).add(it);
        }
        return groups.values().stream().map(g -> new UpcomingRow(g.getFirst().getDueDate(),
                titles.getOrDefault(g.getFirst().getLessonId(), g.getFirst().getLessonId()),
                g.stream().map(it -> Math.min(it.getStage(), 3)).toList(),
                g.stream().filter(it -> it.getItemType() != ReviewItem.Type.QUIZ).count())).toList();
    }

    private static String clip(String text, int max) {
        String t = text.replaceAll("\\s+", " ").strip();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }

    /** 教材から消えた問題は出さない。 */
    private boolean stillExists(ReviewItem item) {
        if (item.getItemType() == ReviewItem.Type.QUIZ) {
            return content.lesson(item.getLessonId()).map(l -> item.quizIndex() < l.quiz().size()).orElse(false);
        }
        return catalog.find(item.getItemRef()).isPresent();
    }
}
