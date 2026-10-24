package com.quizverse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * "Challenge a friend": a short code for one finished exam quiz or mock. The quiz itself comes from the
 * creator's attempt. Scores are self-reported like all history, which is fine between friends.
 */
@Entity
@Table(name = "challenge")
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id")
    private QuizAttempt attempt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getCode() { return code; }
    public QuizAttempt getAttempt() { return attempt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setCode(String code) { this.code = code; }
    public void setAttempt(QuizAttempt attempt) { this.attempt = attempt; }
}
