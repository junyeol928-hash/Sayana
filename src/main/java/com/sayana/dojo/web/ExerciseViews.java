package com.sayana.dojo.web;

import java.util.List;

import org.springframework.stereotype.Component;

import com.sayana.dojo.ai.AiService;
import com.sayana.dojo.content.ExerciseDef;
import com.sayana.dojo.progress.Submission;
import com.sayana.dojo.progress.SubmissionRepository;

@Component
public class ExerciseViews {

    private final SubmissionRepository submissions;
    private final AiService ai;

    public ExerciseViews(SubmissionRepository submissions, AiService ai) {
        this.submissions = submissions;
        this.ai = ai;
    }

    public ExerciseView of(ExerciseDef ex) {
        List<Submission> subs = submissions.findByExerciseKeyOrderByCreatedAtDesc(ex.key());
        String code = subs.isEmpty() ? ex.starter() : subs.get(0).getCode();
        boolean passed = subs.stream().anyMatch(Submission::isPassed);
        return new ExerciseView(ex, code, passed, subs.size(), ai.hintsFor(ex.key()), null);
    }

    /** 復習用:何も見ずに雛形から書き直す。 */
    public ExerciseView forReview(ExerciseDef ex, long reviewItemId) {
        return new ExerciseView(ex, ex.starter(), false, 0, List.of(), reviewItemId);
    }
}
