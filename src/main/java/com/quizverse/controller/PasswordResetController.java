package com.quizverse.controller;

import com.quizverse.config.StorageMode;
import com.quizverse.mail.Mailer;
import com.quizverse.model.PasswordResetToken;
import com.quizverse.model.User;
import com.quizverse.repository.PasswordResetTokenRepository;
import com.quizverse.repository.UserRepository;
import com.quizverse.security.RateLimiter;
import com.quizverse.security.SessionTracker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Forgot password. The request always answers the same way, whether or not the email has an account, so it
 * can't be used to find out who is registered. The emailed link carries a random token; only its SHA-256 hash
 * is stored. A link works once, for 30 minutes, and using it logs out every session of the account.
 */
@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetController.class);
    static final Duration LIFETIME = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final SessionTracker sessions;
    private final Mailer mailer;
    private final RateLimiter limiter;
    private final Clock clock;
    private final String publicUrl;
    private final boolean testMode;

    public PasswordResetController(UserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder encoder,
                                   SessionTracker sessions, Mailer mailer, RateLimiter limiter, Clock clock,
                                   StorageMode storage, @Value("${quizverse.public-url:}") String publicUrl) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.sessions = sessions;
        this.mailer = mailer;
        this.limiter = limiter;
        this.clock = clock;
        this.publicUrl = publicUrl.trim().replaceAll("/+$", "");
        this.testMode = storage.isTemporary();
    }

    public record ResetRequest(@NotBlank @Size(max = 254) String email) {}

    public record ResetConfirm(
            @NotBlank @Size(max = 100) String token,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String newPassword) {}

    private static final Map<String, String> SENT = Map.of("message",
            "If an account uses that email, we've sent a link to reset the password. It works for 30 minutes.");

    @PostMapping
    @Transactional
    public ResponseEntity<?> request(@Valid @RequestBody ResetRequest req, HttpServletRequest http) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        // Per address (stops mail-bombing one inbox) and per IP (stops sweeping many addresses).
        if (!limiter.tryAcquire("reset-ip:" + http.getRemoteAddr(), 10, Duration.ofHours(1))
                || !limiter.tryAcquire("reset-email:" + email, 3, Duration.ofHours(1))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many reset requests. Please try again later."));
        }
        String base = baseUrl();
        if (base == null) {
            log.warn("Password reset requested but QUIZVERSE_PUBLIC_URL is not set; no email sent");
            return ResponseEntity.ok(SENT);
        }
        users.findByEmail(email).ifPresent(user -> {
            String token = newToken();
            PasswordResetToken row = new PasswordResetToken();
            row.setUser(user);
            row.setTokenHash(hash(token));
            row.setExpiresAt(clock.instant().plus(LIFETIME));
            tokens.save(row);
            mailer.send(user.getEmail(), "Reset your QuizVerse password", """
                    Hi %s,

                    Someone (hopefully you) asked to reset the password for your QuizVerse account "%s".
                    Open this link within 30 minutes to choose a new password:

                    %s/reset-password.html?token=%s

                    If you didn't ask for this, ignore this email; your password stays the same.
                    """.formatted(user.getDisplayName(), user.getUsername(), base, token));
        });
        return ResponseEntity.ok(SENT);
    }

    @PostMapping("/confirm")
    @Transactional
    public ResponseEntity<?> confirm(@Valid @RequestBody ResetConfirm req) {
        PasswordResetToken row = tokens.findByTokenHash(hash(req.token())).orElse(null);
        if (row == null || row.isUsed() || !clock.instant().isBefore(row.getExpiresAt())) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "This reset link has expired or was already used. Please ask for a new one."));
        }
        User user = row.getUser();
        user.setPasswordHash(encoder.encode(req.newPassword()));
        tokens.useAllForUser(user.getId());
        sessions.invalidateAll(user.getUsername(), null);
        return ResponseEntity.noContent().build();
    }

    /** The configured site address; in local test mode, whatever address the request came in on. */
    private String baseUrl() {
        if (!publicUrl.isEmpty()) return publicUrl;
        return testMode ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() : null;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
