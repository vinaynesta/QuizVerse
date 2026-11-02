package com.quizverse.controller;

import com.quizverse.dto.AttemptDtos.AnswerRequest;
import com.quizverse.dto.AttemptDtos.AttemptRequest;
import com.quizverse.dto.AttemptDtos.AttemptResponse;
import com.quizverse.dto.AttemptDtos.BestScore;
import com.quizverse.model.AttemptAnswer;
import com.quizverse.model.Challenge;
import com.quizverse.model.QuizAttempt;
import com.quizverse.model.User;
import com.quizverse.repository.AttemptAnswerRepository;
import com.quizverse.repository.ChallengeRepository;
import com.quizverse.repository.QuizAttemptRepository;
import com.quizverse.repository.UserRepository;
import com.quizverse.security.QuizTimer;
import com.quizverse.service.QuizCatalog;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Quiz history for the logged-in user. Practice sets are scored in the browser (their answers are public, for
 * offline use), so those results are self-reported. Exam quizzes and mocks are re-scored here from the server's
 * answer key whenever the answers are sent, and become verified (ranked on leaderboards) with a receipt from
 * POST /api/quiz/submit proving they were finished in time.
 */
@RestController
@RequestMapping("/api/attempts")
public class AttemptController {

    private final QuizAttemptRepository attempts;
    private final AttemptAnswerRepository answers;
    private final UserRepository users;
    private final ChallengeRepository challenges;
    private final QuizCatalog catalog;
    private final QuizTimer timer;

    public AttemptController(QuizAttemptRepository attempts, AttemptAnswerRepository answers, UserRepository users,
                             ChallengeRepository challenges, QuizCatalog catalog, QuizTimer timer) {
        this.attempts = attempts;
        this.answers = answers;
        this.users = users;
        this.challenges = challenges;
        this.catalog = catalog;
        this.timer = timer;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AttemptResponse save(@Valid @RequestBody AttemptRequest req, Principal principal) {
        User user = users.findByUsername(principal.getName()).orElseThrow();
        QuizAttempt a = new QuizAttempt();
        a.setUser(user);
        a.setKind(req.kind() == null ? "exam" : req.kind());
        a.setExamId(req.examId());
        a.setStageId(req.stageId());
        a.setSectionId(req.sectionId());
        a.setLevelId(req.levelId());
        a.setQuizIndex(req.quizIndex());
        a.setQuizTitle(req.quizTitle());
        a.setMaxMarks(req.maxMarks());
        a.setScore(Math.min(req.score(), req.maxMarks()));
        a.setCorrect(req.correct());
        a.setWrong(req.wrong());
        a.setSkipped(req.skipped());
        a.setTimed(req.timed());
        a.setTimeUsedSeconds(req.timeUsedSeconds());
        if (req.challengeCode() != null) {
            challenges.findByCode(req.challengeCode())
                    .filter(c -> sameQuiz(c.getAttempt(), a) && !c.getAttempt().getUser().getId().equals(user.getId()))
                    .ifPresent(a::setChallenge);
        }
        List<AttemptAnswer> rows = req.answers() == null ? List.of()
                : req.answers().stream().map(r -> toAnswer(a, r)).toList();
        rescore(a, rows, req.receipt());
        attempts.save(a);
        answers.saveAll(rows);
        return AttemptResponse.of(a);
    }

    /** Newest first. */
    @GetMapping
    public List<AttemptResponse> list(Principal principal) {
        User user = users.findByUsername(principal.getName()).orElseThrow();
        return attempts.findByUserIdOrderByCompletedAtDesc(user.getId()).stream().map(AttemptResponse::of).toList();
    }

    @GetMapping("/best")
    public List<BestScore> best(Principal principal) {
        User user = users.findByUsername(principal.getName()).orElseThrow();
        return attempts.bestScores(user.getId()).stream()
                .map(r -> new BestScore(r[0] + "/" + r[1] + "/" + r[2] + "/" + r[3] + "/" + r[4],
                        (Double) r[5], (Double) r[6], (Long) r[7]))
                .toList();
    }

    /**
     * Exam quizzes and mocks: when the answers match the quiz's questions, the score and each answer's
     * correctness come from the server's key, not the browser. A valid, unused receipt makes it verified.
     */
    private void rescore(QuizAttempt a, List<AttemptAnswer> rows, String receipt) {
        if (!"exam".equals(a.getKind()) && !"mock".equals(a.getKind())) return;
        QuizCatalog.Quiz quiz = catalog.find(QuizCatalog.key(a.getExamId(), a.getStageId(), a.getSectionId(),
                a.getLevelId(), a.getQuizIndex()));
        if (quiz == null || rows.size() != quiz.questionCount()) return;
        List<String> ids = catalog.ids(quiz);
        for (int i = 0; i < rows.size(); i++) {
            if (!ids.get(i).equals(rows.get(i).getQuestionId())) return;
        }
        List<Integer> chosen = rows.stream().map(AttemptAnswer::getChosen).toList();
        QuizCatalog.Score score = catalog.score(quiz, chosen);
        for (int i = 0; i < rows.size(); i++) rows.get(i).setCorrect(score.correctness().get(i));
        a.setScore(score.score());
        a.setMaxMarks(score.maxMarks());
        a.setCorrect(score.correct());
        a.setWrong(score.wrong());
        a.setSkipped(score.skipped());
        a.setTimeUsedSeconds(Math.min(a.getTimeUsedSeconds(), quiz.minutes() * 60));
        if (timer.checkReceipt(receipt, quiz.key(), chosen)) {
            String hash = sha256(receipt);
            if (!attempts.existsByReceiptHash(hash)) {
                a.setVerified(true);
                a.setReceiptHash(hash);
            }
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Objects.requireNonNull(text).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A challenge only counts attempts at its own quiz; the creator's retakes stay ordinary history. */
    private static boolean sameQuiz(QuizAttempt x, QuizAttempt y) {
        return x.getKind().equals(y.getKind()) && x.getExamId().equals(y.getExamId())
                && x.getStageId().equals(y.getStageId()) && x.getSectionId().equals(y.getSectionId())
                && x.getLevelId().equals(y.getLevelId()) && x.getQuizIndex() == y.getQuizIndex();
    }

    private static AttemptAnswer toAnswer(QuizAttempt attempt, AnswerRequest r) {
        AttemptAnswer a = new AttemptAnswer();
        a.setAttempt(attempt);
        a.setQuestionId(r.questionId());
        a.setSubject(r.subject());
        a.setTopic(r.topic());
        a.setChosen(r.chosen());
        // A skipped question is never correct, whatever the client says.
        a.setCorrect(r.chosen() != null && r.correct());
        a.setSeconds(r.seconds());
        return a;
    }
}
