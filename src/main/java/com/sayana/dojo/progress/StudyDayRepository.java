package com.sayana.dojo.progress;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyDayRepository extends JpaRepository<StudyDay, LocalDate> {

    List<StudyDay> findByDayGreaterThanEqualOrderByDayAsc(LocalDate from);
}
