package com.quizverse.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** Stores an authenticated user in the session: used by password login, sign-up and Google sign-in. */
@Component
public class SessionSignIn {

    private final SecurityContextRepository contextRepository;
    private final SessionTracker sessions;

    public SessionSignIn(SecurityContextRepository contextRepository, SessionTracker sessions) {
        this.contextRepository = contextRepository;
        this.sessions = sessions;
    }

    public void signIn(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        // New session id on login, so a session id planted before login is useless afterwards.
        if (request.getSession(false) != null) request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        sessions.register(auth.getName(), request.getSession());
    }
}
