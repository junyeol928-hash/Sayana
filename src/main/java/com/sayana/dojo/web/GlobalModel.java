package com.sayana.dojo.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.sayana.dojo.ai.AiService;
import com.sayana.dojo.progress.ActivityService;
import com.sayana.dojo.progress.ReviewService;

/** 全画面のヘッダーで使う値。 */
@ControllerAdvice
public class GlobalModel {

    private final AiService ai;
    private final ReviewService review;
    private final ActivityService activity;
    private final UiHelper ui;

    public GlobalModel(AiService ai, ReviewService review, ActivityService activity, UiHelper ui) {
        this.ai = ai;
        this.review = review;
        this.activity = activity;
        this.ui = ui;
    }

    @ModelAttribute("ui")
    public UiHelper ui() {
        return ui;
    }

    @ModelAttribute("aiAvailable")
    public boolean aiAvailable() {
        return ai.available();
    }

    @ModelAttribute("dueCount")
    public int dueCount() {
        return review.due().size();
    }

    @ModelAttribute("streak")
    public int streak() {
        return activity.currentStreak();
    }
}
