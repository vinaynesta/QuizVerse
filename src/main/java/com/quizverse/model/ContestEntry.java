package com.quizverse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** A user's registration for a contest, and their result once submitted. */
@Entity
@Table(name = "contest_entry")
public class ContestEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_id")
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Instant registeredAt = Instant.now();

    private Instant startedAt;
    private Instant submittedAt;

    /** Answers in the paper's original order: comma-separated option indexes, "-" for skipped. */
    @Column(length = 1500)
    private String chosen;

    private Double score;
    private Integer correct;
    private Integer wrong;
    private Integer skipped;
    private Integer secondsUsed;

    public Long getId() { return id; }
    public Contest getContest() { return contest; }
    public User getUser() { return user; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public String getChosen() { return chosen; }
    public Double getScore() { return score; }
    public Integer getCorrect() { return correct; }
    public Integer getWrong() { return wrong; }
    public Integer getSkipped() { return skipped; }
    public Integer getSecondsUsed() { return secondsUsed; }

    public void setContest(Contest contest) { this.contest = contest; }
    public void setUser(User user) { this.user = user; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public void setChosen(String chosen) { this.chosen = chosen; }
    public void setScore(Double score) { this.score = score; }
    public void setCorrect(Integer correct) { this.correct = correct; }
    public void setWrong(Integer wrong) { this.wrong = wrong; }
    public void setSkipped(Integer skipped) { this.skipped = skipped; }
    public void setSecondsUsed(Integer secondsUsed) { this.secondsUsed = secondsUsed; }
}
