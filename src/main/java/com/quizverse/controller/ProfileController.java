package com.quizverse.controller;

import com.quizverse.dto.AuthDtos.ChangePasswordRequest;
import com.quizverse.dto.AuthDtos.DeleteAccountRequest;
import com.quizverse.dto.AuthDtos.UpdateProfileRequest;
import com.quizverse.dto.AuthDtos.UserResponse;
import com.quizverse.model.User;
import com.quizverse.repository.UserRepository;
import com.quizverse.security.AppUserDetailsService;
import com.quizverse.security.SessionTracker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Locale;
import java.util.Map;

/** Profile page: edit name and email, change password, delete the account. The username can't change. */
@RestController
@RequestMapping("/api/me")
public class ProfileController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppUserDetailsService userDetails;
    private final SessionTracker sessions;

    public ProfileController(UserRepository users, PasswordEncoder encoder, AppUserDetailsService userDetails,
                             SessionTracker sessions) {
        this.users = users;
        this.encoder = encoder;
        this.userDetails = userDetails;
        this.sessions = sessions;
    }

    @PutMapping
    @Transactional
    public ResponseEntity<?> update(@Valid @RequestBody UpdateProfileRequest req, Principal principal) {
        User user = users.findByUsername(principal.getName()).orElseThrow();
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmail(email).filter(other -> !other.getId().equals(user.getId())).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "An account with that email already exists."));
        }
        user.setDisplayName(req.displayName().trim());
        user.setEmail(email);
        return ResponseEntity.ok(UserResponse.of(user, userDetails.isAdmin(user.getUsername())));
    }

    /** Keeps this session logged in and logs out every other one, e.g. a phone someone else has. */
    @PostMapping("/password")
    @Transactional
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest req, Principal principal,
                                            HttpServletRequest request) {
        User user = users.findByUsername(principal.getName()).orElseThrow();
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) return wrongPassword();
        user.setPasswordHash(encoder.encode(req.newPassword()));
        sessions.invalidateAll(user.getUsername(), request.getSession(false));
        return ResponseEntity.noContent().build();
    }

    /** Removes the user and, through ON DELETE CASCADE, their attempts, answers, bookmarks and reports. */
    @DeleteMapping
    @Transactional
    public ResponseEntity<?> delete(@Valid @RequestBody DeleteAccountRequest req, Principal principal) {
        User user = users.findByUsername(principal.getName()).orElseThrow();
        if (!encoder.matches(req.password(), user.getPasswordHash())) return wrongPassword();
        users.delete(user);
        sessions.invalidateAll(user.getUsername(), null);
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    // 400 rather than 401: the frontend reads 401 as "not logged in".
    private static ResponseEntity<Map<String, String>> wrongPassword() {
        return ResponseEntity.badRequest().body(Map.of("error", "Your current password is wrong."));
    }
}
