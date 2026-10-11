package com.sayana.dojo.progress;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** 確認クイズの解答1回分。answers は "0,2,-1,..." の形(-1 は未回答)。 */
@Entity
@Table(indexes = @Index(columnList = "lessonId"))
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String lessonId;

    private int score;
    private int total;
    private String answers;

    @Column(nullable = false)
    private Instant createdAt;

    protected QuizAttempt() {
    }

    public QuizAttempt(String lessonId, int score, int total, String answers, Instant createdAt) {
        this.lessonId = lessonId;
        this.score = score;
        this.total = total;
        this.answers = answers;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getLessonId() { return lessonId; }
    public int getScore() { return score; }
    public int getTotal() { return total; }
    public String getAnswers() { return answers; }
    public Instant getCreatedAt() { return createdAt; }
}
