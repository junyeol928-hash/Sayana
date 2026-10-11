package com.sayana.dojo.web;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.sayana.dojo.ai.AiService;
import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.Lesson;
import com.sayana.dojo.progress.ExerciseCatalog;
import com.sayana.dojo.progress.LearningService;
import com.sayana.dojo.progress.ProgressService;

@Controller
public class LessonController {

    private final ContentRepository content;
    private final ProgressService progress;
    private final LearningService learning;
    private final ExerciseViews exerciseViews;
    private final ExerciseCatalog catalog;
    private final AiService ai;

    public LessonController(ContentRepository content, ProgressService progress, LearningService learning,
                            ExerciseViews exerciseViews, ExerciseCatalog catalog, AiService ai) {
        this.content = content;
        this.progress = progress;
        this.learning = learning;
        this.exerciseViews = exerciseViews;
        this.catalog = catalog;
        this.ai = ai;
    }

    @GetMapping("/lessons/{id}")
    public String lesson(@PathVariable String id, Model model) {
        Lesson lesson = find(id);
        if (lesson.ready()) {
            learning.visit(id);
        }
        model.addAttribute("lesson", lesson);
        model.addAttribute("status", progress.status(lesson));
        model.addAttribute("part", content.part(lesson.partId()).orElseThrow());
        model.addAttribute("chapter", content.chapter(lesson.chapterId()).orElseThrow());
        model.addAttribute("prev", content.previous(id).orElse(null));
        model.addAttribute("next", content.next(id).orElse(null));
        model.addAttribute("exercises", lesson.exercises().stream().map(exerciseViews::of).toList());
        model.addAttribute("generated", catalog.generatedFor(id).stream().map(exerciseViews::of).toList());
        model.addAttribute("chat", ai.chatHistory(id));
        return "lesson";
    }

    /** 「読んだ!」ボタン。 */
    @PostMapping("/lessons/{id}/read")
    public String read(@PathVariable String id, Model model) {
        find(id);
        learning.markRead(id);
        model.addAttribute("lessonId", id);
        return "fragments/lesson-parts :: readDone";
    }

    @PostMapping("/lessons/{id}/quiz")
    public String quiz(@PathVariable String id, @RequestParam Map<String, String> params, Model model) {
        Lesson lesson = find(id);
        Map<Integer, Integer> answers = new HashMap<>();
        for (int i = 0; i < lesson.quiz().size(); i++) {
            String v = params.get("q" + i);
            if (v != null && v.matches("\\d+")) {
                answers.put(i, Integer.parseInt(v));
            }
        }
        model.addAttribute("lesson", lesson);
        model.addAttribute("outcome", learning.submitQuiz(lesson, answers));
        model.addAttribute("status", progress.status(lesson));
        return "fragments/lesson-parts :: quizResult";
    }

    @PostMapping("/lessons/{id}/checklist")
    public String checklist(@PathVariable String id, @RequestParam String key, Model model) {
        Lesson lesson = find(id);
        var item = lesson.checklist().stream().filter(c -> c.key().equals(key)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean checked = learning.toggleChecklist(id, key);
        model.addAttribute("lesson", lesson);
        model.addAttribute("item", item);
        model.addAttribute("checked", checked);
        return "fragments/lesson-parts :: checklistItem";
    }

    private Lesson find(String id) {
        return content.lesson(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "レッスンが見つかりません"));
    }
}
