package com.sayana.dojo.progress;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HintRecordRepository extends JpaRepository<HintRecord, Long> {

    List<HintRecord> findByExerciseKeyOrderByLevelAscIdAsc(String exerciseKey);
}
