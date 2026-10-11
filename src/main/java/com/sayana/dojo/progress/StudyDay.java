package com.sayana.dojo.progress;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 学習した日(連続学習日数の計算用)。 */
@Entity
public class StudyDay {

    @Id
    @Column(name = "study_date")
    private LocalDate day;

    private int actions;

    protected StudyDay() {
    }

    public StudyDay(LocalDate day) {
        this.day = day;
    }

    public LocalDate getDay() { return day; }
    public int getActions() { return actions; }
    public void increment() { actions++; }
}
