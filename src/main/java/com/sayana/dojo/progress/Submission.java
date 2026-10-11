package com.sayana.dojo.progress;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** 演習の提出1回分。採点結果と AI の講評も一緒に残す。 */
@Entity
@Table(indexes = {@Index(columnList = "exerciseKey"), @Index(columnList = "lessonId")})
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String exerciseKey;

    @Column(nullable = false)
    private String lessonId;

    private String exerciseTitle;

    @Lob
    @Column(nullable = false)
    private String code;

    /** PASSED / FAILED / COMPILE_ERROR / TIMEOUT / ERROR */
    @Column(nullable = false)
    private String status;

    private int passedCount;
    private int totalCount;

    /** 採点結果(GradeResult)の JSON。 */
    @Lob
    private String resultJson;

    @Lob
    private String aiReview;

    @Lob
    private String errorExplanation;

    /** 復習キューからの提出か。 */
    private boolean fromReview;

    @Column(nullable = false)
    private Instant createdAt;

    protected Submission() {
    }

    public Submission(String exerciseKey, String lessonId, String exerciseTitle, String code, String status,
                      int passedCount, int totalCount, String resultJson, boolean fromReview, Instant createdAt) {
        this.exerciseKey = exerciseKey;
        this.lessonId = lessonId;
        this.exerciseTitle = exerciseTitle;
        this.code = code;
        this.status = status;
        this.passedCount = passedCount;
        this.totalCount = totalCount;
        this.resultJson = resultJson;
        this.fromReview = fromReview;
        this.createdAt = createdAt;
    }

    public boolean isPassed() {
        return "PASSED".equals(status);
    }

    public Long getId() { return id; }
    public String getExerciseKey() { return exerciseKey; }
    public String getLessonId() { return lessonId; }
    public String getExerciseTitle() { return exerciseTitle; }
    public String getCode() { return code; }
    public String getStatus() { return status; }
    public int getPassedCount() { return passedCount; }
    public int getTotalCount() { return totalCount; }
    public String getResultJson() { return resultJson; }
    public String getAiReview() { return aiReview; }
    public void setAiReview(String aiReview) { this.aiReview = aiReview; }
    public String getErrorExplanation() { return errorExplanation; }
    public void setErrorExplanation(String errorExplanation) { this.errorExplanation = errorExplanation; }
    public boolean isFromReview() { return fromReview; }
    public Instant getCreatedAt() { return createdAt; }
}
