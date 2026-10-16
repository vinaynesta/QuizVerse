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

/** One question within a finished quiz: what was picked and how long it took. */
@Entity
@Table(name = "attempt_answer")
public class AttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id")
    private QuizAttempt attempt;

    @Column(nullable = false, length = 16) private String questionId;
    @Column(nullable = false, length = 32) private String subject;
    @Column(nullable = false, length = 64) private String topic;
    /** Option index, or null when skipped. */
    private Integer chosen;
    @Column(nullable = false) private boolean correct;
    @Column(nullable = false) private int seconds;

    public Long getId() { return id; }
    public QuizAttempt getAttempt() { return attempt; }
    public String getQuestionId() { return questionId; }
    public String getSubject() { return subject; }
    public String getTopic() { return topic; }
    public Integer getChosen() { return chosen; }
    public boolean isCorrect() { return correct; }
    public int getSeconds() { return seconds; }

    public void setAttempt(QuizAttempt attempt) { this.attempt = attempt; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setTopic(String topic) { this.topic = topic; }
    public void setChosen(Integer chosen) { this.chosen = chosen; }
    public void setCorrect(boolean correct) { this.correct = correct; }
    public void setSeconds(int seconds) { this.seconds = seconds; }
}
