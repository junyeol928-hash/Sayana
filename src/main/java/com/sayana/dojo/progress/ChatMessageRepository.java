package com.sayana.dojo.progress;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByLessonIdOrderByIdAsc(String lessonId);

    List<ChatMessage> findByLessonIdOrderByIdDesc(String lessonId, Pageable pageable);
}
