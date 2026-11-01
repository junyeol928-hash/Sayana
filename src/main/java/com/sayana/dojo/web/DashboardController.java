package com.sayana.dojo.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.sayana.dojo.ai.AiService;
import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.progress.ActivityService;
import com.sayana.dojo.progress.ProgressService;
import com.sayana.dojo.progress.ReviewService;
import com.sayana.dojo.progress.SubmissionRepository;

@Controller
public class DashboardController {

    private final ProgressService progress;
    private final ActivityService activity;
    private final ReviewService review;
    private final SubmissionRepository submissions;
    private final ContentRepository content;
    private final AiService ai;

    public DashboardController(ProgressService progress, ActivityService activity, ReviewService review,
                               SubmissionRepository submissions, ContentRepository content, AiService ai) {
        this.progress = progress;
        this.activity = activity;
        this.review = review;
        this.submissions = submissions;
        this.content = content;
        this.ai = ai;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        var parts = progress.overview();
        model.addAttribute("parts", parts);
        model.addAttribute("nextLesson", progress.nextLesson().orElse(null));
        model.addAttribute("weakLessons", progress.weakLessons(5));
        model.addAttribute("days", activity.recentDays(56));
        model.addAttribute("studiedToday", activity.studiedToday());
        model.addAttribute("totalDays", activity.totalDays());
        model.addAttribute("upcoming", review.upcoming().size());
        model.addAttribute("graduated", review.graduatedCount());
        model.addAttribute("recentSubmissions", submissions.findTop50ByOrderByCreatedAtDesc().stream().limit(5).toList());
        model.addAttribute("completedLessons", parts.stream().mapToLong(ProgressService.PartProgress::completed).sum());
        model.addAttribute("readyLessons", content.readyLessons().size());
        model.addAttribute("totalLessons", content.allLessons().size());
        model.addAttribute("lessonTitles", LessonTitles.of(content));
        // 染まり具合(公開レッスンを順に並べた帯と、段ごとの数)
        List<ProgressService.LessonStatus> flat = parts.stream().flatMap(p -> p.chapters().stream())
                .flatMap(c -> c.lessons().stream()).filter(s -> s.lesson().ready()).toList();
        int[] dyeCounts = new int[5];
        flat.forEach(s -> dyeCounts[s.dyeLevel()]++);
        model.addAttribute("flatLessons", flat);
        model.addAttribute("dyeCounts", dyeCounts);
        model.addAttribute("deepCount", dyeCounts[2] + dyeCounts[3] + dyeCounts[4]);
        progress.nextLesson().ifPresent(next -> {
            model.addAttribute("nextIndex", content.readyLessons().indexOf(next) + 1);
            model.addAttribute("nextChapter", content.chapter(next.chapterId()).map(c -> c.title()).orElse(""));
        });
        return "dashboard";
    }

    /** 教材を読み直す(教材を書きながら確認する用)。 */
    @PostMapping("/content/reload")
    public String reload() {
        content.reload();
        ai.clearContextCache();
        return "redirect:/";
    }
}
