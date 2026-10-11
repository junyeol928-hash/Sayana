package com.sayana.dojo.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.sayana.dojo.ai.AiException;
import com.sayana.dojo.ai.AiService;
import com.sayana.dojo.progress.GeneratedExercise;

import jakarta.servlet.http.HttpServletResponse;

/** AI 機能の htmx エンドポイント。失敗したら画面にエラーの断片を返す。 */
@Controller
public class AiController {

    private final AiService ai;

    public AiController(AiService ai) {
        this.ai = ai;
    }

    @PostMapping("/ai/chat")
    public String chat(@RequestParam String lessonId, @RequestParam String question,
                       @RequestParam(required = false) String code, Model model) {
        if (question.isBlank()) {
            return error(model, "質問を入力してください。");
        }
        try {
            model.addAttribute("messages", ai.askTutor(lessonId, question, code));
            return "fragments/ai :: chatMessages";
        } catch (AiException e) {
            return error(model, e.getMessage());
        }
    }

    @PostMapping("/ai/chat/clear")
    public String clearChat(@RequestParam String lessonId, Model model) {
        ai.clearChat(lessonId);
        model.addAttribute("messages", java.util.List.of());
        return "fragments/ai :: chatMessages";
    }

    @PostMapping("/ai/review/{submissionId}")
    public String review(@PathVariable long submissionId, Model model) {
        try {
            model.addAttribute("text", ai.review(submissionId).getAiReview());
            model.addAttribute("title", "AIコードレビュー");
            return "fragments/ai :: answer";
        } catch (AiException e) {
            return error(model, e.getMessage());
        }
    }

    @PostMapping("/ai/explain/{submissionId}")
    public String explainSubmission(@PathVariable long submissionId, Model model) {
        try {
            model.addAttribute("text", ai.explainSubmission(submissionId).getErrorExplanation());
            model.addAttribute("title", "AIエラー翻訳");
            return "fragments/ai :: answer";
        } catch (AiException e) {
            return error(model, e.getMessage());
        }
    }

    @PostMapping("/ai/explain-run")
    public String explainRun(@RequestParam(required = false) String lessonId, @RequestParam String code,
                             @RequestParam String error, Model model) {
        try {
            String lesson = lessonId == null || lessonId.isBlank() ? null : lessonId;
            model.addAttribute("text", ai.explain(lesson, code, error, null));
            model.addAttribute("title", "AIエラー翻訳");
            return "fragments/ai :: answer";
        } catch (AiException e) {
            return error(model, e.getMessage());
        }
    }

    @PostMapping("/ai/generate")
    public String generate(@RequestParam String lessonId, Model model, HttpServletResponse response) {
        try {
            GeneratedExercise g = ai.generateExercise(lessonId);
            // 作れたら類題のページへ移動
            response.setHeader("HX-Redirect", "/practice/" + g.getId());
            return "fragments/ai :: empty";
        } catch (AiException e) {
            return error(model, e.getMessage());
        }
    }

    private static String error(Model model, String message) {
        model.addAttribute("error", message);
        return "fragments/ai :: error";
    }
}
