package com.quizverse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/** A scheduled live contest: everyone sits the same paper (a catalog quiz or mock) in the same window. */
@Entity
@Table(name = "contest")
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    /** QuizCatalog key, e.g. "ibps-clerk/prelims/mock/medium/2". */
    @Column(nullable = false, length = 200)
    private String quizKey;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private int minutes;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getQuizKey() { return quizKey; }
    public Instant getStartsAt() { return startsAt; }
    public int getMinutes() { return minutes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEndsAt() { return startsAt.plus(Duration.ofMinutes(minutes)); }

    public void setTitle(String title) { this.title = title; }
    public void setQuizKey(String quizKey) { this.quizKey = quizKey; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public void setMinutes(int minutes) { this.minutes = minutes; }
}
