package com.quizverse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// "user" is a reserved word in PostgreSQL, hence app_user.
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Stored lowercase so lookups are case-insensitive.
    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 60)
    private String displayName;

    /** Null for an account created with Google until the user sets a password. */
    @Column(length = 100)
    private String passwordHash;

    /** The Google account's stable id ("sub"), once the user has signed in with Google. */
    @Column(unique = true)
    private String googleSubject;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Quizzes per day the user aims for. */
    @Column(nullable = false)
    private int dailyGoal = 2;

    /** Opt-in: "you haven't practised in 3 days". */
    @Column(nullable = false)
    private boolean emailReminders;

    /** Opt-in: the Monday progress email. */
    @Column(nullable = false)
    private boolean emailWeekly;

    /** Random value in the one-click unsubscribe link; made with the first reminder or summary. */
    @Column(unique = true, length = 43)
    private String emailToken;

    private Instant lastReminderAt;

    private Instant lastWeeklyAt;

    /** Shown as "Anonymous" on leaderboards and contest results. */
    @Column(nullable = false)
    private boolean leaderboardHidden;

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public int getDailyGoal() { return dailyGoal; }
    public String getGoogleSubject() { return googleSubject; }
    public boolean hasPassword() { return passwordHash != null; }
    public boolean isEmailReminders() { return emailReminders; }
    public boolean isEmailWeekly() { return emailWeekly; }
    public String getEmailToken() { return emailToken; }
    public Instant getLastReminderAt() { return lastReminderAt; }
    public Instant getLastWeeklyAt() { return lastWeeklyAt; }
    public boolean isLeaderboardHidden() { return leaderboardHidden; }

    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setDailyGoal(int dailyGoal) { this.dailyGoal = dailyGoal; }
    public void setGoogleSubject(String googleSubject) { this.googleSubject = googleSubject; }
    public void setEmailReminders(boolean emailReminders) { this.emailReminders = emailReminders; }
    public void setEmailWeekly(boolean emailWeekly) { this.emailWeekly = emailWeekly; }
    public void setEmailToken(String emailToken) { this.emailToken = emailToken; }
    public void setLastReminderAt(Instant lastReminderAt) { this.lastReminderAt = lastReminderAt; }
    public void setLastWeeklyAt(Instant lastWeeklyAt) { this.lastWeeklyAt = lastWeeklyAt; }
    public void setLeaderboardHidden(boolean leaderboardHidden) { this.leaderboardHidden = leaderboardHidden; }
}
