package com.quizverse.controller;

import com.quizverse.model.Challenge;
import com.quizverse.model.QuizAttempt;
import com.quizverse.model.User;
import com.quizverse.repository.ChallengeRepository;
import com.quizverse.repository.QuizAttemptRepository;
import com.quizverse.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Challenge a friend: turn one of your finished exam quizzes or mocks into a link. Anyone with the link can
 * see the quiz and the scores; taking it needs an account, so scores have names. Scores are self-reported,
 * like all history (the answers are public JSON), which is fine between friends but not for a leaderboard.
 */
@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    /** No 0/O, 1/I/L: codes get read out and typed in. */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ChallengeRepository challenges;
    private final QuizAttemptRepository attempts;
    private final UserRepository users;

    public ChallengeController(ChallengeRepository challenges, QuizAttemptRepository attempts, UserRepository users) {
        this.challenges = challenges;
        this.attempts = attempts;
        this.users = users;
    }

    /** The quiz to retake, keyed like the attempt (and like quiz.html's parameters). */
    public record QuizRef(String kind, String examId, String stageId, String sectionId, String levelId, int quizIndex,
                          String quizTitle, boolean timed) {
        static QuizRef of(QuizAttempt a) {
            return new QuizRef(a.getKind(), a.getExamId(), a.getStageId(), a.getSectionId(), a.getLevelId(),
                    a.getQuizIndex(), a.getQuizTitle(), a.isTimed());
        }
    }

    /**
     * {@code you} marks the viewer's own row. Display names only: usernames and emails stay private. Each friend
     * is ranked by their first try through the link, so retaking until a lucky score doesn't count.
     */
    public record Entry(String name, double score, double maxMarks, int correct, int wrong, int skipped,
                        int timeUsedSeconds, Instant completedAt, boolean creator, boolean you) {}

    public record ChallengeResponse(String code, QuizRef quiz, Entry creator, List<Entry> entries) {}

    /** Creates the challenge for one of your attempts, or returns the existing one (one code per attempt). */
    @PostMapping("/from-attempt/{attemptId}")
    @Transactional
    public ResponseEntity<?> create(@PathVariable Long attemptId, Principal principal) {
        User user = users.findByUsername(principal.getName()).orElseThrow();
        QuizAttempt attempt = attempts.findByIdAndUserId(attemptId, user.getId()).orElse(null);
        if (attempt == null) return error(HttpStatus.NOT_FOUND, "Quiz result not found.");
        if (!"exam".equals(attempt.getKind()) && !"mock".equals(attempt.getKind())) {
            return error(HttpStatus.BAD_REQUEST, "Only exam quizzes and full mocks can be challenges.");
        }
        Challenge challenge = challenges.findByAttemptId(attempt.getId()).orElseGet(() -> {
            Challenge c = new Challenge();
            c.setCode(newCode());
            c.setAttempt(attempt);
            return challenges.save(c);
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(view(challenge, user));
    }

    /** Public, so a link opened by someone logged out still shows what they're being challenged to. */
    @GetMapping("/{code}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> get(@PathVariable String code, Principal principal) {
        Challenge challenge = code.matches("[A-Z0-9]{1,10}") ? challenges.findByCode(code).orElse(null) : null;
        if (challenge == null) return error(HttpStatus.NOT_FOUND, "This challenge doesn't exist or was deleted.");
        User viewer = principal == null ? null : users.findByUsername(principal.getName()).orElse(null);
        return ResponseEntity.ok(view(challenge, viewer));
    }

    private ChallengeResponse view(Challenge challenge, User viewer) {
        QuizAttempt original = challenge.getAttempt();
        Long viewerId = viewer == null ? null : viewer.getId();
        Set<Long> seen = new HashSet<>();
        List<Entry> entries = attempts.challengeEntries(challenge.getId()).stream()
                .filter(a -> seen.add(a.getUser().getId()))
                .map(a -> entry(a, false, viewerId))
                .sorted(Comparator.comparingDouble(Entry::score).reversed().thenComparing(Entry::completedAt))
                .toList();
        return new ChallengeResponse(challenge.getCode(), QuizRef.of(original), entry(original, true, viewerId), entries);
    }

    private static Entry entry(QuizAttempt a, boolean creator, Long viewerId) {
        return new Entry(a.getUser().getDisplayName(), a.getScore(), a.getMaxMarks(), a.getCorrect(), a.getWrong(),
                a.getSkipped(), a.getTimeUsedSeconds(), a.getCompletedAt(), creator,
                a.getUser().getId().equals(viewerId));
    }

    private String newCode() {
        while (true) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            if (!challenges.existsByCode(code.toString())) return code.toString();
        }
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
