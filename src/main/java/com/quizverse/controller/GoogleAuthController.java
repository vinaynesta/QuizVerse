package com.quizverse.controller;

import com.quizverse.dto.AuthDtos.GoogleSignInRequest;
import com.quizverse.dto.AuthDtos.UserResponse;
import com.quizverse.model.User;
import com.quizverse.repository.UserRepository;
import com.quizverse.security.AppUserDetailsService;
import com.quizverse.security.GoogleTokenVerifier;
import com.quizverse.security.GoogleTokenVerifier.GoogleAccount;
import com.quizverse.security.SessionSignIn;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Locale;
import java.util.Map;

/**
 * "Sign in with Google". The button gives the page an ID token, which is posted here and verified.
 * <ul>
 *   <li>A Google account already connected to a user signs that user in.</li>
 *   <li>Otherwise, if no account uses the email, a new account is created (no password; a username is made
 *       from the email).</li>
 *   <li>If an account with that email exists, it is <b>not</b> joined automatically: sign-up doesn't verify
 *       emails, so someone could have registered with another person's address. The owner logs in with
 *       their password and connects Google from the profile page instead.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class GoogleAuthController {

    private final GoogleTokenVerifier verifier;
    private final UserRepository users;
    private final AppUserDetailsService userDetails;
    private final SessionSignIn sessionSignIn;

    public GoogleAuthController(GoogleTokenVerifier verifier, UserRepository users, AppUserDetailsService userDetails,
                                SessionSignIn sessionSignIn) {
        this.verifier = verifier;
        this.users = users;
        this.userDetails = userDetails;
        this.sessionSignIn = sessionSignIn;
    }

    @PostMapping("/auth/google")
    @Transactional
    public ResponseEntity<?> signIn(@Valid @RequestBody GoogleSignInRequest req, HttpServletRequest request,
                                    HttpServletResponse response) {
        if (!verifier.isEnabled()) return error(HttpStatus.NOT_FOUND, "Google sign-in isn't set up on this server.");
        GoogleAccount google;
        try {
            google = verifier.verify(req.credential());
        } catch (JwtException e) {
            return error(HttpStatus.UNAUTHORIZED, "Google sign-in failed. Please try again.");
        }

        User user = users.findByGoogleSubject(google.subject()).orElse(null);
        boolean created = false;
        if (user == null) {
            if (google.email() == null || !google.emailVerified()) {
                return error(HttpStatus.BAD_REQUEST, "Your Google account has no verified email address.");
            }
            String email = google.email().trim().toLowerCase(Locale.ROOT);
            if (users.existsByEmail(email)) {
                return error(HttpStatus.CONFLICT, "An account with this email already exists. Log in with your "
                        + "password, then connect Google on your profile page.");
            }
            user = new User();
            user.setUsername(freeUsername(email));
            user.setEmail(email);
            user.setDisplayName(displayName(google.name(), email));
            user.setGoogleSubject(google.subject());
            users.save(user);
            created = true;
        }

        UserDetails details = userDetails.loadUserByUsername(user.getUsername());
        sessionSignIn.signIn(UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()),
                request, response);
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(response(user));
    }

    /** Connects a Google account to the logged-in user, so they can sign in with either. */
    @PostMapping("/me/google")
    @Transactional
    public ResponseEntity<?> link(@Valid @RequestBody GoogleSignInRequest req, Principal principal) {
        if (!verifier.isEnabled()) return error(HttpStatus.NOT_FOUND, "Google sign-in isn't set up on this server.");
        GoogleAccount google;
        try {
            google = verifier.verify(req.credential());
        } catch (JwtException e) {
            return error(HttpStatus.UNAUTHORIZED, "Google sign-in failed. Please try again.");
        }
        User user = users.findByUsername(principal.getName()).orElseThrow();
        User owner = users.findByGoogleSubject(google.subject()).orElse(null);
        if (owner != null && !owner.getId().equals(user.getId())) {
            return error(HttpStatus.CONFLICT, "That Google account is already connected to another QuizVerse account.");
        }
        user.setGoogleSubject(google.subject());
        return ResponseEntity.ok(response(user));
    }

    /** Disconnects Google. Only allowed with a password set, so the user can still log in. */
    @DeleteMapping("/me/google")
    @Transactional
    public ResponseEntity<?> unlink(Principal principal) {
        User user = users.findByUsername(principal.getName()).orElseThrow();
        if (!user.hasPassword()) {
            return error(HttpStatus.BAD_REQUEST, "Set a password first, so you can still log in without Google.");
        }
        user.setGoogleSubject(null);
        return ResponseEntity.ok(response(user));
    }

    /** "asha.k+quiz@gmail.com" becomes "asha_k_quiz", then "asha_k_quiz2", ... if taken. Never an admin name. */
    private String freeUsername(String email) {
        String base = email.substring(0, email.indexOf('@')).replaceAll("[^a-z0-9_]", "_");
        if (base.length() > 24) base = base.substring(0, 24);
        if (base.length() < 3) base += "_user";
        String candidate = base;
        for (int n = 2; users.existsByUsername(candidate) || userDetails.isAdmin(candidate); n++) {
            candidate = base + n;
        }
        return candidate;
    }

    private static String displayName(String name, String email) {
        String n = name == null || name.isBlank() ? email.substring(0, email.indexOf('@')) : name.trim();
        return n.length() > 60 ? n.substring(0, 60) : n;
    }

    private UserResponse response(User user) {
        return UserResponse.of(user, userDetails.isAdmin(user.getUsername()));
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
