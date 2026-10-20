package com.quizverse.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Streaks from quiz completion times. Days are Indian Standard Time, the exams' audience, so a quiz
 * at 1 a.m. IST counts for that day rather than the previous UTC one.
 */
public final class Streaks {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private Streaks() {}

    /**
     * {@code current} counts consecutive days ending today, or yesterday if nothing is done yet today,
     * so the streak isn't shown as broken before the day is over.
     */
    public record Summary(int current, int best, int today, boolean activeToday) {}

    public static LocalDate day(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    public static Summary of(Collection<Instant> completions, LocalDate today) {
        Map<LocalDate, Long> perDay = completions.stream()
                .collect(Collectors.groupingBy(Streaks::day, TreeMap::new, Collectors.counting()));

        int best = 0, run = 0;
        LocalDate previous = null;
        for (LocalDate d : perDay.keySet()) {
            run = previous != null && previous.plusDays(1).equals(d) ? run + 1 : 1;
            best = Math.max(best, run);
            previous = d;
        }

        int todayCount = perDay.getOrDefault(today, 0L).intValue();
        LocalDate d = todayCount > 0 ? today : today.minusDays(1);
        int current = 0;
        while (perDay.containsKey(d)) {
            current++;
            d = d.minusDays(1);
        }
        return new Summary(current, best, todayCount, todayCount > 0);
    }
}
