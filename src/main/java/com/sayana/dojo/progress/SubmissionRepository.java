package com.sayana.dojo.progress;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findTop50ByOrderByCreatedAtDesc();

    List<Submission> findByLessonIdOrderByCreatedAtDesc(String lessonId);

    List<Submission> findByExerciseKeyOrderByCreatedAtDesc(String exerciseKey);
}
