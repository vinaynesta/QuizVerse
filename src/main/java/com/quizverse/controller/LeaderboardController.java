package com.quizverse.controller;

import com.quizverse.dto.AuthDtos.LeaderboardPreferenceRequest;
import com.quizverse.dto.AuthDtos.UserResponse;
import com.quizverse.model.QuizAttempt;
import com.quizverse.model.User;
import com.quizverse.repository.QuizAttemptRepository;
import com.quizverse.repository.UserRepository;
import com.quizverse.security.AppUserDetailsService;
import com.quizverse.service.QuizCatalog;
import com.quizverse.service.Streaks;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Leaderboards from verified attempts only (scored on the server, finished in time). Per quiz: each user's best
 * score, ties broken by less time, then whoever got there first. Weekly (Monday to Sunday, IST): total marks
 * from each user's best score on every quiz they took that week. Names only, never usernames; users who
 * hide themselves appear as "Anonymous".
 */
@RestController
public class LeaderboardController {

    static final int TOP = 50;

    private final QuizAttemptRepository attempts;
    private final UserRepository users;
    private final QuizCatalog catalog;
    private final AppUserDetailsService userDetails;
    private final Clock clock;

    public LeaderboardController(QuizAttemptRepository attempts, UserRepository users, QuizCatalog catalog,
                                 AppUserDetailsService userDetails, Clock clock) {
        this.attempts = attempts;
        this.users = users;
        this.catalog = catalog;
        this.userDetails = userDetails;
        this.clock = clock;
    }

    public record Row(int rank, String name, double score, double maxMarks, int timeUsedSeconds, int quizzes,
                      boolean you) {}

    /** {@code you} is the viewer's own row even when it is outside the top 50 (null when not on the board). */
    public record Board(String title, String quiz, int players, List<Row> top, Row you) {}

    @GetMapping("/api/leaderboards/quiz")
    @Transactional(readOnly = true)
    public ResponseEntity<?> quiz(@RequestParam String key, Principal principal) {
        QuizCatalog.Quiz quiz = catalog.find(key);
        if (quiz == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Quiz not found."));
        }
        String[] p = key.split("/");
        String kind = "mock".equals(p[2]) ? "mock" : "exam";
        Map<Long, QuizAttempt> best = new HashMap<>();
        for (QuizAttempt a : attempts.verifiedFor(kind, p[0], p[1], p[2], p[3], Integer.parseInt(p[4]))) {
            best.merge(a.getUser().getId(), a, (x, y) -> BY_RESULT.compare(x, y) <= 0 ? x : y);
        }
        List<QuizAttempt> ranked = best.values().stream().sorted(BY_RESULT).toList();
        Long viewer = viewerId(principal);
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            QuizAttempt a = ranked.get(i);
            rows.add(new Row(i + 1, name(a.getUser()), a.getScore(), a.getMaxMarks(), a.getTimeUsedSeconds(), 1,
                    a.getUser().getId().equals(viewer)));
        }
        return ResponseEntity.ok(board(quiz.title(), key, rows));
    }

    @GetMapping("/api/leaderboards/weekly")
    @Transactional(readOnly = true)
    public Board weekly(Principal principal) {
        LocalDate monday = LocalDate.now(clock.withZone(Streaks.ZONE)).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant since = monday.atStartOfDay(Streaks.ZONE).toInstant();
        // Per user, per quiz: the best verified score this week. Then summed per user.
        Map<Long, Map<String, QuizAttempt>> bestPerQuiz = new HashMap<>();
        Map<Long, User> who = new HashMap<>();
        for (QuizAttempt a : attempts.verifiedSince(since)) {
            who.put(a.getUser().getId(), a.getUser());
            String quiz = QuizCatalog.key(a.getExamId(), a.getStageId(), a.getSectionId(), a.getLevelId(), a.getQuizIndex());
            bestPerQuiz.computeIfAbsent(a.getUser().getId(), k -> new HashMap<>())
                    .merge(quiz, a, (x, y) -> BY_RESULT.compare(x, y) <= 0 ? x : y);
        }
        record Total(Long userId, double score, double max, int time, int quizzes) {}
        List<Total> totals = bestPerQuiz.entrySet().stream().map(e -> new Total(e.getKey(),
                round(e.getValue().values().stream().mapToDouble(QuizAttempt::getScore).sum()),
                e.getValue().values().stream().mapToDouble(QuizAttempt::getMaxMarks).sum(),
                e.getValue().values().stream().mapToInt(QuizAttempt::getTimeUsedSeconds).sum(),
                e.getValue().size()))
                .sorted(Comparator.comparingDouble(Total::score).reversed().thenComparingInt(Total::time))
                .toList();
        Long viewer = viewerId(principal);
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < totals.size(); i++) {
            Total t = totals.get(i);
            rows.add(new Row(i + 1, name(who.get(t.userId())), t.score(), t.max(), t.time(), t.quizzes(),
                    t.userId().equals(viewer)));
        }
        return board("This week (from Monday " + monday + ")", null, rows);
    }

    @PutMapping("/api/me/leaderboard")
    @Transactional
    public UserResponse setHidden(@RequestBody LeaderboardPreferenceRequest req, Principal principal) {
        User user = users.findByUsername(principal.getName()).orElseThrow();
        user.setLeaderboardHidden(req.hidden());
        return UserResponse.of(user, userDetails.isAdmin(user.getUsername()));
    }

    static final Comparator<QuizAttempt> BY_RESULT = Comparator.comparingDouble(QuizAttempt::getScore).reversed()
            .thenComparingInt(QuizAttempt::getTimeUsedSeconds)
            .thenComparing(QuizAttempt::getCompletedAt);

    private static Board board(String title, String quiz, List<Row> rows) {
        Row you = rows.stream().filter(Row::you).findFirst().orElse(null);
        return new Board(title, quiz, rows.size(), rows.subList(0, Math.min(TOP, rows.size())), you);
    }

    static String name(User u) {
        return u.isLeaderboardHidden() ? "Anonymous" : u.getDisplayName();
    }

    private Long viewerId(Principal principal) {
        return principal == null ? null : users.findByUsername(principal.getName()).map(User::getId).orElse(null);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
