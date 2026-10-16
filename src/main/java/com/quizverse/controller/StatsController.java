package com.quizverse.controller;

import com.quizverse.model.User;
import com.quizverse.repository.AttemptAnswerRepository;
import com.quizverse.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-topic accuracy and speed, and the "revise mistakes" list, built from saved answers. */
@RestController
@RequestMapping("/api/me")
public class StatsController {

    private final AttemptAnswerRepository answers;
    private final UserRepository users;

    public StatsController(AttemptAnswerRepository answers, UserRepository users) {
        this.answers = answers;
        this.users = users;
    }

    /**
     * {@code answered} excludes skips; accuracy = correct / answered. Average seconds are over
     * answered questions only, so skipping fast doesn't look like solving fast.
     */
    public record TopicStat(String subject, String topic, long answered, long correct, long skipped,
                            double accuracy, double avgSeconds) {}

    public record Mistake(String questionId, String subject, String topic, Integer lastChosen,
                          Instant lastAnsweredAt, long timesWrong) {}

    @GetMapping("/topics")
    public List<TopicStat> topics(Principal principal) {
        return answers.topicStats(userId(principal)).stream()
                .map(r -> {
                    long answered = num(r[2]), correct = num(r[3]), skipped = num(r[4]), answeredSecs = num(r[6]);
                    return new TopicStat((String) r[0], (String) r[1], answered, correct, skipped,
                            answered == 0 ? 0 : (double) correct / answered,
                            answered == 0 ? 0 : (double) answeredSecs / answered);
                })
                .sorted(Comparator.comparing(TopicStat::subject).thenComparing(TopicStat::topic))
                .toList();
    }

    /** Questions last answered wrong or skipped, newest first. */
    @GetMapping("/mistakes")
    public List<Mistake> mistakes(Principal principal) {
        Long userId = userId(principal);
        Map<String, Long> wrongCounts = new HashMap<>();
        answers.wrongCounts(userId).forEach(r -> wrongCounts.put((String) r[0], num(r[1])));
        return answers.lastAnswerWrong(userId).stream()
                .map(r -> new Mistake((String) r[0], (String) r[1], (String) r[2], (Integer) r[3], (Instant) r[5],
                        wrongCounts.getOrDefault((String) r[0], 0L)))
                .toList();
    }

    private Long userId(Principal principal) {
        User user = users.findByUsername(principal.getName()).orElseThrow();
        return user.getId();
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
