package com.sayana.dojo.progress;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistCheckRepository extends JpaRepository<ChecklistCheck, Long> {

    List<ChecklistCheck> findByLessonId(String lessonId);

    Optional<ChecklistCheck> findByLessonIdAndItemKey(String lessonId, String itemKey);
}
