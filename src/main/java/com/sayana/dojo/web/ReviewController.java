package com.sayana.dojo.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
    public String queue(Model model) {
        List<ReviewItem> due = review.due().stream().filter(this::stillExists).toList();
        model.addAttribute("due", due);
        model.addAttribute("upcomingItems", review.upcoming().stream().filter(this::stillExists).limit(30).toList());
        model.addAttribute("graduated", review.graduatedCount());
        model.addAttribute("lessonTitles", LessonTitles.of(content));
        if (!due.isEmpty()) {
            ReviewItem item = due.get(0);
            model.addAttribute("item", item);
            Lesson lesson = content.lesson(item.getLessonId()).orElse(null);
            model.addAttribute("itemLesson", lesson);
            if (item.getItemType() == ReviewItem.Type.QUIZ) {
                model.addAttribute("question", lesson.quiz().get(item.quizIndex()));
            } else {
                model.addAttribute("view", views.forReview(catalog.find(item.getItemRef()).orElseThrow(), item.getId()));
            }
        }
        return "review";
    }

    @PostMapping("/review/{id}/quiz")
    public String answerQuiz(@PathVariable long id, @RequestParam(defaultValue = "-1") int choice, Model model) {
        ReviewItem item = items.findById(id).orElseThrow();
        Lesson lesson = content.lesson(item.getLessonId()).orElseThrow();
        boolean correct = learning.answerReviewQuiz(item, choice);
        ReviewItem updated = items.findById(id).orElseThrow();
        model.addAttribute("correct", correct);
        model.addAttribute("choice", choice);
        model.addAttribute("question", lesson.quiz().get(item.quizIndex()));
        model.addAttribute("reviewLabel", ReviewService.intervalLabel(updated));
        return "fragments/review-parts :: quizAnswer";
    }

    /** 教材から消えた問題は出さない。 */
    private boolean stillExists(ReviewItem item) {
        if (item.getItemType() == ReviewItem.Type.QUIZ) {
            return content.lesson(item.getLessonId()).map(l -> item.quizIndex() < l.quiz().size()).orElse(false);
        }
        return catalog.find(item.getItemRef()).isPresent();
    }
}
