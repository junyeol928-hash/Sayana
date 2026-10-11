package com.sayana.dojo.progress;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedExerciseRepository extends JpaRepository<GeneratedExercise, Long> {

    List<GeneratedExercise> findByLessonIdOrderByIdAsc(String lessonId);
}
