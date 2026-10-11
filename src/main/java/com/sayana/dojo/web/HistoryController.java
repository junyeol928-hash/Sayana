package com.sayana.dojo.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.progress.ExerciseCatalog;
import com.sayana.dojo.progress.LearningService;
import com.sayana.dojo.progress.Submission;
import com.sayana.dojo.progress.SubmissionRepository;

/** 提出履歴と AI レビューの見返し。 */
@Controller
public class HistoryController {

    private final SubmissionRepository submissions;
    private final LearningService learning;
    private final ContentRepository content;
    private final ExerciseCatalog catalog;

    public HistoryController(SubmissionRepository submissions, LearningService learning, ContentRepository content,
                             ExerciseCatalog catalog) {
        this.submissions = submissions;
        this.learning = learning;
        this.content = content;
        this.catalog = catalog;
    }

    @GetMapping("/history")
    public String list(@RequestParam(required = false) String lesson, Model model) {
        var list = lesson == null || lesson.isBlank()
                ? submissions.findTop50ByOrderByCreatedAtDesc()
                : submissions.findByLessonIdOrderByCreatedAtDesc(lesson);
        model.addAttribute("submissions", list);
        model.addAttribute("lessonFilter", lesson);
        model.addAttribute("lessons", content.readyLessons());
        model.addAttribute("lessonTitles", LessonTitles.of(content));
        return "history";
    }

    @GetMapping("/history/{id}")
    public String detail(@PathVariable long id, Model model) {
        Submission s = submissions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "提出が見つかりません"));
        model.addAttribute("s", s);
        model.addAttribute("result", learning.readResult(s));
        model.addAttribute("exercise", catalog.find(s.getExerciseKey()).orElse(null));
        model.addAttribute("lessonTitles", LessonTitles.of(content));
        return "submission";
    }
}
