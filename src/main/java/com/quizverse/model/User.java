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

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public int getDailyGoal() { return dailyGoal; }
    public String getGoogleSubject() { return googleSubject; }
    public boolean hasPassword() { return passwordHash != null; }

    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setDailyGoal(int dailyGoal) { this.dailyGoal = dailyGoal; }
    public void setGoogleSubject(String googleSubject) { this.googleSubject = googleSubject; }
}
