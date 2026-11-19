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
import com.sayana.dojo.progress.ProgressService;
import com.sayana.dojo.progress.ReviewService;

/** 演習の提出・採点と段階ヒント。演習はキー(lessonId/ex1 や gen/12)で指定する。 */
@Controller
public class ExerciseController {

    private final ExerciseCatalog catalog;
    private final LearningService learning;
    private final AiService ai;
    private final ExerciseViews views;
    private final ContentRepository content;
    private final ProgressService progress;

    public ExerciseController(ExerciseCatalog catalog, LearningService learning, AiService ai, ExerciseViews views,
                              ContentRepository content, ProgressService progress) {
        this.catalog = catalog;
        this.learning = learning;
        this.ai = ai;
        this.views = views;
        this.content = content;
        this.progress = progress;
    }

    @PostMapping("/exercises/submit")
    public String submit(@RequestParam String key, @RequestParam String code,
                         @RequestParam(required = false) Long reviewItemId, Model model) {
        ExerciseDef ex = find(key);
        // レッスンの演習なら、提出の前後の染まりを比べる(藍に染まった瞬間を採点結果で知らせ、段の帯・本文の軸・冒頭の色見本を
        // その場で書き換えるため。開き直すまで縹のままで、軸の「演習」も縫われなかった。28 回目の審査)。AI の類題は染まりに数えない
        var lesson = ex.generated() ? null : content.lesson(ex.lessonId()).orElse(null);
        int before = lesson == null ? 0 : progress.status(lesson).dyeLevel();
        var outcome = learning.submit(ex, code, reviewItemId);
        if (lesson != null) {
            var status = progress.status(lesson);
            model.addAttribute("status", status);
            model.addAttribute("dyeBefore", before);
            model.addAttribute("dyedTo", status.dyeLevel() > before ? status.dyeLabel() : null);
        }
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
