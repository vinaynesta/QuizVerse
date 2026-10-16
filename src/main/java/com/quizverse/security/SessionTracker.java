package com.quizverse.security;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which sessions belong to which user, so changing the password can log out the user's
 * other devices and deleting the account can log out all of them. Sessions live in this server's
 * memory, so an in-memory map sees every one of them. Spring Boot registers this as a servlet listener.
 */
@Component
public class SessionTracker implements HttpSessionListener {

    private final Map<String, Set<HttpSession>> byUser = new ConcurrentHashMap<>();

    /** Called after login; the session object stays the same when its id changes. */
    public void register(String username, HttpSession session) {
        byUser.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    /** Ends every session of the user except {@code keep} (which may be null to end them all). */
    public void invalidateAll(String username, HttpSession keep) {
        Set<HttpSession> sessions = byUser.getOrDefault(username, Set.of());
        for (HttpSession session : sessions) {
            if (session == keep) continue;
            sessions.remove(session);
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalid) {
                // Expired or logged out in the meantime.
            }
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        HttpSession session = event.getSession();
        byUser.values().forEach(sessions -> sessions.remove(session));
        byUser.values().removeIf(Set::isEmpty);
    }
}
