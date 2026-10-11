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

/** AI が作った類題(採点テストつき。模範解答がテストに合格したものだけ保存する)。 */
@Entity
@Table(indexes = @Index(columnList = "lessonId"))
public class GeneratedExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String lessonId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String className;

    private int difficulty;

    @Lob
    @Column(nullable = false)
    private String problem;

    @Lob
    @Column(nullable = false)
    private String starter;

    @Lob
    @Column(nullable = false)
    private String solution;

    @Lob
    @Column(nullable = false)
    private String test;

    private String model;

    @Column(nullable = false)
    private Instant createdAt;

    protected GeneratedExercise() {
    }

    public GeneratedExercise(String lessonId, String title, String className, int difficulty, String problem,
                             String starter, String solution, String test, String model, Instant createdAt) {
        this.lessonId = lessonId;
        this.title = title;
        this.className = className;
        this.difficulty = difficulty;
        this.problem = problem;
        this.starter = starter;
        this.solution = solution;
        this.test = test;
        this.model = model;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getLessonId() { return lessonId; }
    public String getTitle() { return title; }
    public String getClassName() { return className; }
    public int getDifficulty() { return difficulty; }
    public String getProblem() { return problem; }
    public String getStarter() { return starter; }
    public String getSolution() { return solution; }
    public String getTest() { return test; }
    public String getModel() { return model; }
    public Instant getCreatedAt() { return createdAt; }

    public String key() {
        return "gen/" + id;
    }
}
