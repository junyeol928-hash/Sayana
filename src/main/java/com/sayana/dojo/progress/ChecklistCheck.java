package com.sayana.dojo.progress;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** STS手順などの「やった」チェック。 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"lessonId", "itemKey"}))
public class ChecklistCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String lessonId;

    @Column(nullable = false)
    private String itemKey;

    @Column(nullable = false)
    private Instant checkedAt;

    protected ChecklistCheck() {
    }

    public ChecklistCheck(String lessonId, String itemKey, Instant checkedAt) {
        this.lessonId = lessonId;
        this.itemKey = itemKey;
        this.checkedAt = checkedAt;
    }

    public Long getId() { return id; }
    public String getLessonId() { return lessonId; }
    public String getItemKey() { return itemKey; }
    public Instant getCheckedAt() { return checkedAt; }
}
