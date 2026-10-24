package com.quizverse.controller;

import com.quizverse.model.User;
import com.quizverse.repository.QuizAttemptRepository;
import com.quizverse.repository.UserRepository;
import com.quizverse.service.Streaks;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;

/** Day streak, today's progress towards the daily goal, and whether today's daily quiz and tech challenge are done. */
@RestController
@RequestMapping("/api/me")
public class StreakController {

    private final QuizAttemptRepository attempts;
    private final UserRepository users;
    private final Clock clock;

    public StreakController(QuizAttemptRepository attempts, UserRepository users, Clock clock) {
        this.attempts = attempts;
        this.users = users;
        this.clock = clock;
    }

    /** {@code date} is today in IST (yyyy-mm-dd), the same date the daily quiz is picked by. */
    public record StreakResponse(int current, int best, int today, int goal, boolean dailyDone, boolean techDailyDone,
                                 String date) {}

    public record GoalRequest(@Min(1) @Max(10) int goal) {}

    @GetMapping("/streak")
    public StreakResponse streak(Principal principal) {
        return response(users.findByUsername(principal.getName()).orElseThrow());
    }

    @PutMapping("/goal")
    @Transactional
    public StreakResponse setGoal(@Valid @RequestBody GoalRequest req, Principal principal) {
        User user = users.findByUsername(principal.getName()).orElseThrow();
        user.setDailyGoal(req.goal());
        return response(user);
    }

    private StreakResponse response(User user) {
        LocalDate today = LocalDate.now(clock.withZone(Streaks.ZONE));
        Streaks.Summary s = Streaks.of(attempts.completionTimes(user.getId()), today);
        boolean dailyDone = attempts.existsByUserIdAndKindAndSectionId(user.getId(), "daily", today.toString());
        boolean techDailyDone = attempts.existsByUserIdAndKindAndSectionId(user.getId(), "tech-daily", today.toString());
        return new StreakResponse(s.current(), s.best(), s.today(), user.getDailyGoal(), dailyDone, techDailyDone,
                today.toString());
    }
}
