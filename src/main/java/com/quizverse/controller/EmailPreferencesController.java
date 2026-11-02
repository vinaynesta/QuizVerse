package com.quizverse.controller;

import com.quizverse.dto.AuthDtos.EmailPreferencesRequest;
import com.quizverse.dto.AuthDtos.UserResponse;
import com.quizverse.model.User;
import com.quizverse.repository.UserRepository;
import com.quizverse.security.AppUserDetailsService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * Turning reminder and weekly emails on or off. Unsubscribing needs no login: the email's link carries the
 * user's random email token. Mail apps post to it directly (RFC 8058 one-click), so it is exempt from CSRF
 * in SecurityConfig; the token is what proves the request is genuine.
 */
@RestController
public class EmailPreferencesController {

    private final UserRepository users;
    private final AppUserDetailsService userDetails;

    public EmailPreferencesController(UserRepository users, AppUserDetailsService userDetails) {
        this.users = users;
        this.userDetails = userDetails;
    }

    @PutMapping("/api/me/email-preferences")
    @Transactional
    public UserResponse update(@RequestBody EmailPreferencesRequest req, Principal principal) {
        User user = users.findByUsername(principal.getName()).orElseThrow();
        user.setEmailReminders(req.reminders());
        user.setEmailWeekly(req.weekly());
        return UserResponse.of(user, userDetails.isAdmin(user.getUsername()));
    }

    /** Turns off every QuizVerse email. An unknown token answers the same, so tokens can't be probed. */
    @PostMapping("/api/email/unsubscribe")
    @Transactional
    public ResponseEntity<Map<String, String>> unsubscribe(@RequestParam(required = false) String token) {
        if (token != null && token.length() <= 64) {
            users.findByEmailToken(token).ifPresent(user -> {
                user.setEmailReminders(false);
                user.setEmailWeekly(false);
            });
        }
        return ResponseEntity.ok(Map.of("message", "You're unsubscribed from QuizVerse emails."));
    }
}
