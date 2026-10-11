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

/** 段階ヒント(1=方向性, 2=手がかり, 3=ほぼ答え)。 */
@Entity
@Table(indexes = @Index(columnList = "exerciseKey"))
public class HintRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String exerciseKey;

    private int level;

    @Lob
    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    private Instant createdAt;

    protected HintRecord() {
    }

    public HintRecord(String exerciseKey, int level, String text, Instant createdAt) {
        this.exerciseKey = exerciseKey;
        this.level = level;
        this.text = text;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getExerciseKey() { return exerciseKey; }
    public int getLevel() { return level; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }

    public String levelLabel() {
        return switch (level) {
            case 1 -> "ヒント1:方向性";
            case 2 -> "ヒント2:手がかり";
            default -> "ヒント3:ほぼ答え";
        };
    }
}
