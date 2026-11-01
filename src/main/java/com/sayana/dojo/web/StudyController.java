package com.sayana.dojo.web;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.Lesson;
import com.sayana.dojo.content.Term;

/**
 * 全レッスン横断の「読解」と「用語集」。答え合わせはブラウザの中(static/js/app.js)で行う。
 */
@Controller
public class StudyController {

    private final ContentRepository content;

    public StudyController(ContentRepository content) {
        this.content = content;
    }

    /** レッスンごとの読解問題。 */
    public record LessonReading(Lesson lesson, List<Lesson.ReadingQuestion> questions) {
    }

    @GetMapping("/reading")
    public String reading(Model model) {
        List<LessonReading> groups = content.readyLessons().stream().filter(l -> !l.reading().isEmpty())
                .map(l -> new LessonReading(l, l.reading())).toList();
        model.addAttribute("groups", groups);
        model.addAttribute("total", groups.stream().mapToInt(g -> g.questions().size()).sum());
        return "reading";
    }

    @GetMapping("/glossary")
    public String glossary(Model model) {
        // 読み順に並べる(同じ用語がレッスンごとに別の説明で出るので、隣り合わせにする)
        Collator ja = Collator.getInstance(Locale.JAPANESE);
        List<Term> terms = content.glossary().stream()
                .sorted(Comparator.comparing((Term t) -> t.read() != null ? t.read() : t.term(), ja))
                .toList();
        model.addAttribute("terms", terms);
        model.addAttribute("lessonTitles", LessonTitles.of(content));
        return "glossary";
    }
}
