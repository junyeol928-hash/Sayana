package com.sayana.dojo.progress;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** レッスンごとの足あと(開いた・読んだ・サンプルを動かした)。 */
@Entity
public class LessonProgress {

    @Id
    private String lessonId;

    private Instant firstVisitedAt;
    private Instant lastVisitedAt;
    private Instant readAt;
    private Instant sampleRunAt;

    protected LessonProgress() {
    }

    public LessonProgress(String lessonId) {
        this.lessonId = lessonId;
    }

    public String getLessonId() { return lessonId; }
    public Instant getFirstVisitedAt() { return firstVisitedAt; }
    public void setFirstVisitedAt(Instant firstVisitedAt) { this.firstVisitedAt = firstVisitedAt; }
    public Instant getLastVisitedAt() { return lastVisitedAt; }
    public void setLastVisitedAt(Instant lastVisitedAt) { this.lastVisitedAt = lastVisitedAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public Instant getSampleRunAt() { return sampleRunAt; }
    public void setSampleRunAt(Instant sampleRunAt) { this.sampleRunAt = sampleRunAt; }
}
