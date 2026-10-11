package com.sayana.dojo.progress;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.ExerciseDef;
import com.sayana.dojo.content.MarkdownRenderer;

/** 教材の演習と、AI生成の類題を同じ {@link ExerciseDef} として引けるようにする。 */
@Component
public class ExerciseCatalog {

    private final ContentRepository content;
    private final GeneratedExerciseRepository generated;
    private final MarkdownRenderer markdown;

    public ExerciseCatalog(ContentRepository content, GeneratedExerciseRepository generated,
                           MarkdownRenderer markdown) {
        this.content = content;
        this.generated = generated;
        this.markdown = markdown;
    }

    public Optional<ExerciseDef> find(String key) {
        if (key != null && key.startsWith(ExerciseDef.GENERATED_PREFIX)) {
            try {
                long id = Long.parseLong(key.substring(ExerciseDef.GENERATED_PREFIX.length()));
                return generated.findById(id).map(this::toDef);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return content.exercise(key);
    }

    public List<ExerciseDef> generatedFor(String lessonId) {
        return generated.findByLessonIdOrderByIdAsc(lessonId).stream().map(this::toDef).toList();
    }

    public ExerciseDef toDef(GeneratedExercise g) {
        // AI の出力なので HTML はエスケープして描画する
        return new ExerciseDef(g.key(), g.getLessonId(), g.getTitle(), g.getClassName(), g.getDifficulty(),
                g.getProblem(), markdown.renderUntrusted(g.getProblem()), g.getStarter(), g.getSolution(),
                g.getTest(), g.getLibraries() == null || g.getLibraries().isBlank() ? List.of()
                        : List.of(g.getLibraries().split(",")));
    }
}
