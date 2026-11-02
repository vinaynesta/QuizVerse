package com.quizverse.controller;

import com.quizverse.security.QuizTimer;
import com.quizverse.security.RateLimiter;
import com.quizverse.service.QuizCatalog;
import com.quizverse.service.QuizCatalog.Quiz;
import com.quizverse.service.QuizCatalog.Score;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Server-side scoring for timed exam quizzes and full mocks, whose answers aren't in the public JSON.
 * <ol>
 *   <li>{@code POST /api/quiz/start}: a signed start token (the server's clock, not the browser's).</li>
 *   <li>{@code POST /api/quiz/submit}: the chosen options; returns the score, the answer key and explanations
 *       for the review screen, and, if submitted within the time limit, a signed receipt. Saving the attempt
 *       with that receipt makes it verified, which is what leaderboards rank.</li>
 * </ol>
 * Both work without an account, so a logged-out user still gets their review and can save after logging in.
 */
@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizCatalog catalog;
    private final QuizTimer timer;
    private final RateLimiter limiter;

    public QuizController(QuizCatalog catalog, QuizTimer timer, RateLimiter limiter) {
        this.catalog = catalog;
        this.timer = timer;
        this.limiter = limiter;
    }

    public record StartRequest(@NotBlank @Size(max = 200) String quiz) {}

    public record StartResponse(String startToken, int minutes, int questions) {}

    public record SubmitRequest(@NotBlank @Size(max = 200) String quiz, @Size(max = 400) String startToken,
                                @NotNull @Size(max = 500) List<@Min(0) @Max(9) Integer> chosen) {}

    /**
     * {@code receipt} is null (and {@code onTime} false) when the start token is missing, wrong or too old.
     * {@code hiExplanations} holds Hindi solutions where a question has Hindi text, else null entries.
     */
    public record SubmitResponse(Score result, List<Integer> answers, List<String> explanations,
                                 List<String> hiExplanations, boolean onTime, String receipt) {}

    @PostMapping("/start")
    public ResponseEntity<?> start(@Valid @RequestBody StartRequest req) {
        Quiz quiz = catalog.find(req.quiz());
        if (quiz == null) return notFound();
        return ResponseEntity.ok(new StartResponse(timer.start(quiz.key()), quiz.minutes(), quiz.questionCount()));
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@Valid @RequestBody SubmitRequest req, HttpServletRequest http) {
        if (!limiter.tryAcquire("submit-ip:" + http.getRemoteAddr(), 120, Duration.ofHours(1))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many quizzes submitted. Please wait a while."));
        }
        Quiz quiz = catalog.find(req.quiz());
        if (quiz == null) return notFound();
        if (req.chosen().size() != quiz.questionCount()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Wrong number of answers for this quiz."));
        }
        Score score = catalog.score(quiz, req.chosen());
        QuizTimer.Start start = timer.readStart(req.startToken());
        boolean onTime = start != null && start.quizKey().equals(quiz.key()) && timer.onTime(start, quiz.minutes());
        String receipt = onTime ? timer.receipt(quiz.key(), start.startMillis(), req.chosen()) : null;
        return ResponseEntity.ok(new SubmitResponse(score, catalog.answers(quiz), catalog.explanations(quiz),
                catalog.hiExplanations(quiz), onTime, receipt));
    }

    private static ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Quiz not found."));
    }
}
