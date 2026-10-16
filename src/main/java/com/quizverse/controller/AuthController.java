package com.quizverse.controller;

import com.quizverse.dto.AuthDtos.LoginRequest;
import com.quizverse.dto.AuthDtos.RegisterRequest;
import com.quizverse.dto.AuthDtos.UserResponse;
import com.quizverse.model.User;
import com.quizverse.repository.UserRepository;
import com.quizverse.security.AppUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepository;
    private final AppUserDetailsService userDetails;

    public AuthController(UserRepository users, PasswordEncoder encoder, AuthenticationManager authManager,
                          SecurityContextRepository contextRepository, AppUserDetailsService userDetails) {
        this.users = users;
        this.encoder = encoder;
        this.authManager = authManager;
        this.contextRepository = contextRepository;
        this.userDetails = userDetails;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest request,
                                      HttpServletResponse response) {
        String username = req.username().toLowerCase(Locale.ROOT);
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username)) return conflict("That username is taken.");
        if (users.existsByEmail(email)) return conflict("An account with that email already exists.");

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(req.displayName().trim());
        user.setPasswordHash(encoder.encode(req.password()));
        users.save(user);

        signIn(username, req.password(), request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response(user));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request,
                                   HttpServletResponse response) {
        try {
            Authentication auth = signIn(req.login(), req.password(), request, response);
            return ResponseEntity.ok(response(users.findByUsername(auth.getName()).orElseThrow()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Wrong username/email or password."));
        }
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /** Hands out the CSRF token; also sets the XSRF-TOKEN cookie if the page has none yet. */
    @GetMapping("/auth/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    /** The logged-in user, or 401 (the frontend uses this to decide what to show). */
    @GetMapping("/me")
    public UserResponse me(Principal principal) {
        return response(users.findByUsername(principal.getName()).orElseThrow());
    }

    private UserResponse response(User user) {
        return UserResponse.of(user, userDetails.isAdmin(user.getUsername()));
    }

    private Authentication signIn(String login, String password, HttpServletRequest request,
                                  HttpServletResponse response) {
        Authentication auth = authManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(login, password));
        // New session id on login, so a session id planted before login is useless afterwards.
        if (request.getSession(false) != null) request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return auth;
    }

    private static ResponseEntity<Map<String, String>> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", message));
    }
}
