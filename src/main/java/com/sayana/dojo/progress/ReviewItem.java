package com.sayana.dojo.progress;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 復習キューの1項目。クイズ1問、または演習1問。
 * stage は間隔反復の段階で、0→1→2→3 と進むたびに次回までの日数が 1→3→7→14 日になる。
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"itemType", "itemRef"}))
public class ReviewItem {

    public enum Type { QUIZ, EXERCISE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type itemType;

    /** QUIZ なら "lessonId#問題番号"、EXERCISE なら演習キー。 */
    @Column(nullable = false)
    private String itemRef;

    @Column(nullable = false)
    private String lessonId;

    private int stage;
    private LocalDate dueDate;
    private int lapses;
    private int reviewCount;
    private boolean graduated;
    private Boolean lastCorrect;
    private Instant lastReviewedAt;
    private Instant createdAt;

    protected ReviewItem() {
    }

    public ReviewItem(Type itemType, String itemRef, String lessonId, LocalDate dueDate, Instant createdAt) {
        this.itemType = itemType;
        this.itemRef = itemRef;
        this.lessonId = lessonId;
        this.dueDate = dueDate;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Type getItemType() { return itemType; }
    public String getItemRef() { return itemRef; }
    public String getLessonId() { return lessonId; }
    public int getStage() { return stage; }
    public void setStage(int stage) { this.stage = stage; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public int getLapses() { return lapses; }
    public void setLapses(int lapses) { this.lapses = lapses; }
    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
    public boolean isGraduated() { return graduated; }
    public void setGraduated(boolean graduated) { this.graduated = graduated; }
    public Boolean getLastCorrect() { return lastCorrect; }
    public void setLastCorrect(Boolean lastCorrect) { this.lastCorrect = lastCorrect; }
    public Instant getLastReviewedAt() { return lastReviewedAt; }
    public void setLastReviewedAt(Instant lastReviewedAt) { this.lastReviewedAt = lastReviewedAt; }
    public Instant getCreatedAt() { return createdAt; }

    /** QUIZ の問題番号。 */
    public int quizIndex() {
        return Integer.parseInt(itemRef.substring(itemRef.indexOf('#') + 1));
    }
}
