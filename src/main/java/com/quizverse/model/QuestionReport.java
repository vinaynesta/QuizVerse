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

/** A user flagging a question as wrong, mistyped or unclear. */
@Entity
@Table(name = "question_report")
public class QuestionReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 16) private String questionId;
    @Column(nullable = false, length = 32) private String reason;
    @Column(length = 500) private String note;
    @Column(nullable = false) private boolean resolved;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getQuestionId() { return questionId; }
    public String getReason() { return reason; }
    public String getNote() { return note; }
    public boolean isResolved() { return resolved; }
    public Instant getCreatedAt() { return createdAt; }

    public void setUser(User user) { this.user = user; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
    public void setReason(String reason) { this.reason = reason; }
    public void setNote(String note) { this.note = note; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
}
