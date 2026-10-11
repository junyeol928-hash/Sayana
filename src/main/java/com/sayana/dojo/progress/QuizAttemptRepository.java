package com.sayana.dojo.progress;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    Optional<QuizAttempt> findFirstByLessonIdOrderByCreatedAtDesc(String lessonId);
}
