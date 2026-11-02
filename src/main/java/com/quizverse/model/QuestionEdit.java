package com.quizverse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * An admin's correction to one question (#32), by its stable id. Null fields keep the bank's value. Options are
 * stored as a JSON array. Exported to scripts/question_edits.json and applied by the build scripts.
 */
@Entity
@Table(name = "question_edit")
public class QuestionEdit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16, unique = true) private String questionId;
    @Column(length = 4000) private String question;
    @Column(length = 2000) private String options;
    private Integer answer;
    @Column(length = 4000) private String explanation;
    @Column(nullable = false) private boolean hidden;
    @Column(length = 500) private String note;
    @Column(nullable = false, length = 50) private String editedBy;
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public String getQuestionId() { return questionId; }
    public String getQuestion() { return question; }
    public String getOptions() { return options; }
    public Integer getAnswer() { return answer; }
    public String getExplanation() { return explanation; }
    public boolean isHidden() { return hidden; }
    public String getNote() { return note; }
    public String getEditedBy() { return editedBy; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setQuestionId(String questionId) { this.questionId = questionId; }
    public void setQuestion(String question) { this.question = question; }
    public void setOptions(String options) { this.options = options; }
    public void setAnswer(Integer answer) { this.answer = answer; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public void setNote(String note) { this.note = note; }
    public void setEditedBy(String editedBy) { this.editedBy = editedBy; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
