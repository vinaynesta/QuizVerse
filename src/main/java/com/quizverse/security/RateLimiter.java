package com.quizverse.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts recent events per key (e.g. "reset-ip:1.2.3.4") in this server's memory and refuses more than
 * {@code max} within {@code window}. One server instance only, like the sessions (see SessionTracker).
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Records the event and returns true, or returns false (recording nothing) when over the limit. */
    public boolean tryAcquire(String key, int max, Duration window) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        Deque<Instant> times = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) times.pollFirst();
            if (times.size() >= max) return false;
            times.addLast(now);
        }
        // Keep the map small: drop keys with nothing recent.
        if (events.size() > 10_000) events.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(cutoff);
            }
        });
        return true;
    }

    /** For tests. */
    public void clear() {
        events.clear();
    }
}
