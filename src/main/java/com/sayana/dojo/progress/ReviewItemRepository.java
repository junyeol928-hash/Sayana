package com.sayana.dojo.progress;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long> {

    Optional<ReviewItem> findByItemTypeAndItemRef(ReviewItem.Type itemType, String itemRef);

    List<ReviewItem> findByGraduatedFalseAndDueDateLessThanEqualOrderByDueDateAscIdAsc(LocalDate date);

    List<ReviewItem> findByGraduatedFalseAndDueDateGreaterThanOrderByDueDateAscIdAsc(LocalDate date);

    long countByGraduatedTrue();
}
