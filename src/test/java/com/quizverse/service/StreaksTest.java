package com.quizverse.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreaksTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    /** Noon IST on the given day of October 2026. */
    private static Instant oct(int day) {
        return Instant.parse("2026-10-%02dT06:30:00Z".formatted(day));
    }

    @Test
    void noQuizzesMeansNoStreak() {
        assertEquals(new Streaks.Summary(0, 0, 0, false), Streaks.of(List.of(), TODAY));
    }

    @Test
    void countsConsecutiveDaysEndingToday() {
        Streaks.Summary s = Streaks.of(List.of(oct(15), oct(16), oct(17), oct(17)), TODAY);
        assertEquals(3, s.current());
        assertEquals(3, s.best());
        assertEquals(2, s.today());
    }

    @Test
    void streakSurvivesUntilTheDayIsOver() {
        // Nothing yet today: yesterday's streak still counts.
        Streaks.Summary s = Streaks.of(List.of(oct(15), oct(16)), TODAY);
        assertEquals(2, s.current());
        assertEquals(0, s.today());
    }

    @Test
    void aMissedDayResetsTheCurrentStreakButNotTheBest() {
        Streaks.Summary s = Streaks.of(List.of(oct(10), oct(11), oct(12), oct(13), oct(15)), TODAY);
        assertEquals(0, s.current());
        assertEquals(4, s.best());
    }

    @Test
    void daysAreCountedInIndiaTime() {
        // 20:00 UTC on the 16th is 01:30 IST on the 17th, so this counts for today.
        Streaks.Summary s = Streaks.of(List.of(Instant.parse("2026-10-16T20:00:00Z")), TODAY);
        assertEquals(1, s.today());
        assertEquals(1, s.current());
    }
}
