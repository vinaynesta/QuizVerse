package com.quizverse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** One finished quiz. The ids match the paths in static/data/index.json. */
@Entity
@Table(name = "quiz_attempt", indexes = @Index(name = "idx_attempt_user", columnList = "user_id, completedAt"))
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** "exam" for a quiz from index.json; "revise", "bookmarks" or "custom" for practice sets. */
    @Column(nullable = false, length = 16) private String kind = "exam";
    @Column(nullable = false, length = 64) private String examId;
    @Column(nullable = false, length = 64) private String stageId;
    @Column(nullable = false, length = 64) private String sectionId;
    @Column(nullable = false, length = 64) private String levelId;
    @Column(nullable = false) private int quizIndex;
    @Column(nullable = false, length = 200) private String quizTitle;

    @Column(nullable = false) private double score;
    @Column(nullable = false) private double maxMarks;
    @Column(nullable = false) private int correct;
    @Column(nullable = false) private int wrong;
    @Column(nullable = false) private int skipped;
    @Column(nullable = false) private boolean timed;
    @Column(nullable = false) private int timeUsedSeconds;

    @Column(nullable = false)
    private Instant completedAt = Instant.now();

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getKind() { return kind; }
    public String getExamId() { return examId; }
    public String getStageId() { return stageId; }
    public String getSectionId() { return sectionId; }
    public String getLevelId() { return levelId; }
    public int getQuizIndex() { return quizIndex; }
    public String getQuizTitle() { return quizTitle; }
    public double getScore() { return score; }
    public double getMaxMarks() { return maxMarks; }
    public int getCorrect() { return correct; }
    public int getWrong() { return wrong; }
    public int getSkipped() { return skipped; }
    public boolean isTimed() { return timed; }
    public int getTimeUsedSeconds() { return timeUsedSeconds; }
    public Instant getCompletedAt() { return completedAt; }

    public void setUser(User user) { this.user = user; }
    public void setKind(String kind) { this.kind = kind; }
    public void setExamId(String examId) { this.examId = examId; }
    public void setStageId(String stageId) { this.stageId = stageId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }
    public void setLevelId(String levelId) { this.levelId = levelId; }
    public void setQuizIndex(int quizIndex) { this.quizIndex = quizIndex; }
    public void setQuizTitle(String quizTitle) { this.quizTitle = quizTitle; }
    public void setScore(double score) { this.score = score; }
    public void setMaxMarks(double maxMarks) { this.maxMarks = maxMarks; }
    public void setCorrect(int correct) { this.correct = correct; }
    public void setWrong(int wrong) { this.wrong = wrong; }
    public void setSkipped(int skipped) { this.skipped = skipped; }
    public void setTimed(boolean timed) { this.timed = timed; }
    public void setTimeUsedSeconds(int timeUsedSeconds) { this.timeUsedSeconds = timeUsedSeconds; }
}
