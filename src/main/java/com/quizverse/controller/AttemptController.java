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
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * Quiz history for the logged-in user. Scoring happens in the browser (the answers are
 * public JSON), so these are self-reported results: fine for personal history, not for
 * a competitive leaderboard.
 */
@RestController
@RequestMapping("/api/attempts")
public class AttemptController {

    private final QuizAttemptRepository attempts;
    private final AttemptAnswerRepository answers;
    private final UserRepository users;
    private final ChallengeRepository challenges;

    public AttemptController(QuizAttemptRepository attempts, AttemptAnswerRepository answers, UserRepository users,
                             ChallengeRepository challenges) {
        this.attempts = attempts;
        this.answers = answers;
        this.users = users;
        this.challenges = challenges;
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
        attempts.save(a);
        if (req.answers() != null) {
            answers.saveAll(req.answers().stream().map(r -> toAnswer(a, r)).toList());
        }
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
