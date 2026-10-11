package com.sayana.dojo.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.exec.JShellRunner;
import com.sayana.dojo.exec.RunResult;
import com.sayana.dojo.progress.LearningService;

/** サンプル・プレイグラウンドのコードを JShell で実行する。 */
@Controller
public class RunController {

    private final JShellRunner runner;
    private final LearningService learning;
    private final ContentRepository content;

    public RunController(JShellRunner runner, LearningService learning, ContentRepository content) {
        this.runner = runner;
        this.learning = learning;
        this.content = content;
    }

    @PostMapping("/run")
    public String run(@RequestParam String code, @RequestParam(required = false) String lessonId,
                      @RequestParam(required = false) List<String> libraries, Model model) {
        RunResult result = libraries == null || libraries.isEmpty()
                ? runner.run(code)
                : runner.run(code, java.time.Duration.ofSeconds(20), libraries);
        if (lessonId != null && content.lesson(lessonId).isPresent()) {
            learning.markSampleRun(lessonId);
        }
        model.addAttribute("result", result);
        model.addAttribute("code", code);
        model.addAttribute("lessonId", lessonId);
        return "fragments/run :: result";
    }

    @GetMapping("/playground")
    public String playground(Model model) {
        model.addAttribute("code", """
                // ここは自由に試せる JShell の砂場です。Ctrl+Enter でも実行できます。
                var names = java.util.List.of("モカ", "ハル", "Duke");
                for (String n : names) {
                    System.out.println("Hello, " + n + "!");
                }
                names.size()
                """);
        return "playground";
    }
}
