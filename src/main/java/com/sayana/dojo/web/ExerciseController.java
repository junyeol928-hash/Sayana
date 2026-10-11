package com.sayana.dojo.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.sayana.dojo.ai.AiException;
import com.sayana.dojo.ai.AiService;
import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.ExerciseDef;
import com.sayana.dojo.progress.ExerciseCatalog;
import com.sayana.dojo.progress.LearningService;
import com.sayana.dojo.progress.ReviewService;

/** 演習の提出・採点と段階ヒント。演習はキー(lessonId/ex1 や gen/12)で指定する。 */
@Controller
public class ExerciseController {

    private final ExerciseCatalog catalog;
    private final LearningService learning;
    private final AiService ai;
    private final ExerciseViews views;
    private final ContentRepository content;

    public ExerciseController(ExerciseCatalog catalog, LearningService learning, AiService ai, ExerciseViews views,
                              ContentRepository content) {
        this.catalog = catalog;
        this.learning = learning;
        this.ai = ai;
        this.views = views;
        this.content = content;
    }

    @PostMapping("/exercises/submit")
    public String submit(@RequestParam String key, @RequestParam String code,
                         @RequestParam(required = false) Long reviewItemId, Model model) {
        ExerciseDef ex = find(key);
        var outcome = learning.submit(ex, code, reviewItemId);
        model.addAttribute("exercise", ex);
        model.addAttribute("outcome", outcome);
        model.addAttribute("result", outcome.result());
        model.addAttribute("reviewLabel", outcome.reviewItem() == null ? null
                : ReviewService.intervalLabel(outcome.reviewItem()));
        model.addAttribute("fromReview", reviewItemId != null);
        return "fragments/exercise :: gradeResult";
    }

    @PostMapping("/exercises/hint")
    public String hint(@RequestParam String key, @RequestParam(required = false) String code, Model model) {
        ExerciseDef ex = find(key);
        try {
            ai.nextHint(key, code);
        } catch (AiException e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("view", views.of(ex));
        return "fragments/exercise :: hints";
    }

    @PostMapping("/exercises/hint/reset")
    public String resetHints(@RequestParam String key, Model model) {
        ExerciseDef ex = find(key);
        ai.resetHints(key);
        model.addAttribute("view", views.of(ex));
        return "fragments/exercise :: hints";
    }

    /** AI が作った類題を解くページ。 */
    @GetMapping("/practice/{id}")
    public String practice(@PathVariable long id, Model model) {
        ExerciseDef ex = find(ExerciseDef.GENERATED_PREFIX + id);
        model.addAttribute("view", views.of(ex));
        model.addAttribute("lesson", content.lesson(ex.lessonId()).orElse(null));
        return "practice";
    }

    private ExerciseDef find(String key) {
        return catalog.find(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "演習が見つかりません"));
    }
}
