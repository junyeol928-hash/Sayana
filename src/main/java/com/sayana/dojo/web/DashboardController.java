package com.sayana.dojo.web;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
        // 前回の続きに差し替えるとき(static/js/app.js)に使う、レッスンごとの「次の段へ進む条件」
        model.addAttribute("dyeHints", flat.stream().collect(Collectors.toMap(st -> st.lesson().id(),
                st -> Objects.requireNonNullElse(nextDyeHint(st), ""))));
        model.addAttribute("dyeCounts", dyeCounts);
        model.addAttribute("deepCount", dyeCounts[2] + dyeCounts[3] + dyeCounts[4]);
        // 数字の欄はスマホ版とそろえる(次の復習・読解の正解はブラウザ側で数える・この 7 日の稽古)
        var upcomingItems = review.upcoming();
        model.addAttribute("nextReview", !review.due().isEmpty() ? "今日"
                : upcomingItems.isEmpty() ? "なし" : upcomingItems.getFirst().getDueDate().format(DateTimeFormatter.ofPattern("M/d")));
        model.addAttribute("weekDays", activity.recentDays(7).stream().filter(d -> d.actions() > 0).count());
        model.addAttribute("readingTotal", content.readyLessons().stream().mapToInt(l -> l.reading().size()).sum());
        progress.nextLesson().ifPresent(next -> {
            flat.stream().filter(s -> s.lesson().id().equals(next.id())).findFirst().ifPresent(s -> {
                model.addAttribute("nextDye", s.dyeLevel());
                model.addAttribute("nextDyeName", s.dyeLabel().split("・")[0]);
                model.addAttribute("nextHint", nextDyeHint(s));
            });
            model.addAttribute("nextIndex", content.readyLessons().indexOf(next) + 1);
            model.addAttribute("nextChapter", content.chapter(next.chapterId()).map(c -> c.title()).orElse(""));
        });
        return "dashboard";
    }

    /** 次の段へ進む条件(スマホ版 app.js の nextDyeHint と同じ文言)。 */
    public static String nextDyeHint(ProgressService.LessonStatus s) {
        return switch (s.dyeLevel()) {
            case 0 -> "読み終えると甕覗きに";
            case 1 -> "クイズで " + s.quizPassScore() + " 問以上取ると縹に";
            case 2 -> "演習に合格すると藍に";
            case 3 -> "復習をすべて卒業すると褐に";
            default -> null;
        };
    }

    /** 教材を読み直す(教材を書きながら確認する用)。 */
    @PostMapping("/content/reload")
    public String reload() {
        content.reload();
        ai.clearContextCache();
        return "redirect:/";
    }
}
