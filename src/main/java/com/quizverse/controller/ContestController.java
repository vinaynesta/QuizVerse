package com.quizverse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizverse.model.Contest;
import com.quizverse.model.ContestEntry;
import com.quizverse.model.User;
import com.quizverse.repository.ContestEntryRepository;
import com.quizverse.repository.ContestRepository;
import com.quizverse.repository.UserRepository;
import com.quizverse.service.QuizCatalog;
import com.quizverse.service.QuizCatalog.Question;
import com.quizverse.service.QuizCatalog.Quiz;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Live contests (admin-scheduled). Users register beforehand; at the start time the server hands each entrant the
 * paper with questions and options shuffled per entrant (seeded by contest and user, so a refresh shows the same
 * order), which makes passing answers around during the contest harder. Answers come back in the entrant's
 * order, are mapped back and scored on the server. Each entrant gets the full time from when they open the
 * paper, but never past the contest's end. Ranks and percentiles are published when the contest ends.
 */
@RestController
@RequestMapping("/api")
public class ContestController {

    /** Late joiners and slow networks: answers are accepted this long after the deadline. */
    static final Duration GRACE = Duration.ofSeconds(90);

    private final ContestRepository contests;
    private final ContestEntryRepository entries;
    private final UserRepository users;
    private final QuizCatalog catalog;
    private final Clock clock;

    public ContestController(ContestRepository contests, ContestEntryRepository entries, UserRepository users,
                             QuizCatalog catalog, Clock clock) {
        this.contests = contests;
        this.entries = entries;
        this.users = users;
        this.catalog = catalog;
        this.clock = clock;
    }

    public record ContestRequest(@NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 200) String quizKey,
                                 @NotNull @Future Instant startsAt, @Min(0) @Max(240) int minutes) {}

    /** {@code state}: upcoming, live or finished. {@code you}: registered, started, submitted, or null. */
    public record ContestView(Long id, String title, String quizKey, Instant startsAt, Instant endsAt, int minutes,
                              int questions, double maxMarks, String state, long registered, String you) {}

    public record PaperQuestion(int n, String id, String section, String question, List<String> options, JsonNode group,
                                boolean needsReview) {}

    public record Paper(Long contestId, String title, Instant deadline, int minutes, List<PaperQuestion> questions) {}

    public record SubmitRequest(@NotNull @Size(max = 500) List<@Min(0) @Max(9) Integer> chosen) {}

    public record ResultRow(int rank, String name, double score, int correct, int wrong, int skipped,
                            int secondsUsed, boolean you) {}

    public record Results(ContestView contest, int participants, List<ResultRow> top, ResultRow you,
                          Double yourPercentile) {}

    // ---------------------------------------------------------------- listing and registration

    private static final List<String> STATE_ORDER = List.of("live", "upcoming", "finished");

    /** Live first, then upcoming, then those finished in the last 30 days. */
    @GetMapping("/contests")
    @Transactional(readOnly = true)
    public List<ContestView> list(Principal principal) {
        Instant now = clock.instant();
        User viewer = viewer(principal);
        Set<Long> mine = viewer == null ? Set.of() : new HashSet<>(entries.contestIdsFor(viewer.getId()));
        return contests.findByStartsAtAfterOrderByStartsAtAsc(now.minus(Duration.ofDays(30))).stream()
                .map(c -> view(c, mine.contains(c.getId()) ? entry(c, viewer) : null, now))
                .sorted(Comparator.comparingInt((ContestView v) -> STATE_ORDER.indexOf(v.state()))
                        // Upcoming soonest first; finished most recent first.
                        .thenComparingLong(v -> "finished".equals(v.state())
                                ? -v.startsAt().getEpochSecond() : v.startsAt().getEpochSecond()))
                .toList();
    }

    @GetMapping("/contests/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> get(@PathVariable Long id, Principal principal) {
        Contest c = contests.findById(id).orElse(null);
        if (c == null) return error(HttpStatus.NOT_FOUND, "Contest not found.");
        return ResponseEntity.ok(view(c, entry(c, viewer(principal)), clock.instant()));
    }

    /** Open until the contest ends: joining late still means the deadline is the contest's end. */
    @PostMapping("/contests/{id}/register")
    @Transactional
    public ResponseEntity<?> register(@PathVariable Long id, Principal principal) {
        Contest c = contests.findById(id).orElse(null);
        if (c == null) return error(HttpStatus.NOT_FOUND, "Contest not found.");
        if (!clock.instant().isBefore(c.getEndsAt())) return error(HttpStatus.BAD_REQUEST, "This contest is over.");
        User user = viewer(principal);
        ContestEntry e = entries.findByContestIdAndUserId(c.getId(), user.getId()).orElseGet(() -> {
            ContestEntry n = new ContestEntry();
            n.setContest(c);
            n.setUser(user);
            return entries.save(n);
        });
        return ResponseEntity.ok(view(c, e, clock.instant()));
    }

    // ---------------------------------------------------------------- sitting the paper

    @GetMapping("/contests/{id}/paper")
    @Transactional
    public ResponseEntity<?> paper(@PathVariable Long id, Principal principal) throws IOException {
        Contest c = contests.findById(id).orElse(null);
        if (c == null) return error(HttpStatus.NOT_FOUND, "Contest not found.");
        Instant now = clock.instant();
        if (now.isBefore(c.getStartsAt())) return error(HttpStatus.FORBIDDEN, "The contest hasn't started yet.");
        if (!now.isBefore(c.getEndsAt())) return error(HttpStatus.FORBIDDEN, "This contest is over.");
        ContestEntry e = entries.findByContestIdAndUserId(c.getId(), viewer(principal).getId()).orElse(null);
        if (e == null) return error(HttpStatus.FORBIDDEN, "Register for the contest first.");
        if (e.getSubmittedAt() != null) return error(HttpStatus.CONFLICT, "You've already submitted this contest.");
        if (e.getStartedAt() == null) e.setStartedAt(now);
        Quiz quiz = catalog.find(c.getQuizKey());
        List<Question> qs = catalog.questions(quiz);
        Order order = order(c, e.getUser(), qs);
        List<PaperQuestion> out = new ArrayList<>();
        for (int n = 0; n < qs.size(); n++) {
            Question q = qs.get(order.questions[n]);
            List<String> opts = new ArrayList<>();
            for (int k : order.options[order.questions[n]]) opts.add(q.options().get(k));
            out.add(new PaperQuestion(n, q.id(), q.section(), q.question(), opts, q.group(), q.needsReview()));
        }
        return ResponseEntity.ok(new Paper(c.getId(), c.getTitle(), deadline(c, e), c.getMinutes(), out));
    }

    /** Answers in the order the paper showed them (shuffled questions and options). */
    @PostMapping("/contests/{id}/submit")
    @Transactional
    public ResponseEntity<?> submit(@PathVariable Long id, @Valid @RequestBody SubmitRequest req, Principal principal)
            throws IOException {
        Contest c = contests.findById(id).orElse(null);
        if (c == null) return error(HttpStatus.NOT_FOUND, "Contest not found.");
        ContestEntry e = entries.findByContestIdAndUserId(c.getId(), viewer(principal).getId()).orElse(null);
        if (e == null || e.getStartedAt() == null) return error(HttpStatus.FORBIDDEN, "You haven't started this contest.");
        if (e.getSubmittedAt() != null) return error(HttpStatus.CONFLICT, "You've already submitted this contest.");
        Instant now = clock.instant();
        if (now.isAfter(deadline(c, e).plus(GRACE))) return error(HttpStatus.FORBIDDEN, "Time is up for this contest.");
        Quiz quiz = catalog.find(c.getQuizKey());
        List<Question> qs = catalog.questions(quiz);
        if (req.chosen().size() != qs.size()) return error(HttpStatus.BAD_REQUEST, "Wrong number of answers.");
        Order order = order(c, e.getUser(), qs);
        // Back to the paper's own order and option positions.
        Integer[] original = new Integer[qs.size()];
        for (int n = 0; n < qs.size(); n++) {
            int qi = order.questions[n];
            Integer shown = req.chosen().get(n);
            original[qi] = shown == null || shown >= order.options[qi].length ? null : order.options[qi][shown];
        }
        QuizCatalog.Score score = catalog.score(quiz, Arrays.asList(original));
        e.setSubmittedAt(now);
        e.setChosen(String.join(",", Arrays.stream(original).map(x -> x == null ? "-" : String.valueOf(x)).toList()));
        e.setScore(score.score());
        e.setCorrect(score.correct());
        e.setWrong(score.wrong());
        e.setSkipped(score.skipped());
        e.setSecondsUsed((int) Math.min(Duration.between(e.getStartedAt(), now).toSeconds(), c.getMinutes() * 60L));
        return ResponseEntity.ok(Map.of("message", "Submitted. Results and your rank appear when the contest ends.",
                "resultsAt", c.getEndsAt().plus(GRACE).toString()));
    }

    // ---------------------------------------------------------------- results

    /** After the contest (plus grace): ranks, percentiles, and the answer key per question. */
    @GetMapping("/contests/{id}/results")
    @Transactional(readOnly = true)
    public ResponseEntity<?> results(@PathVariable Long id, Principal principal) {
        Contest c = contests.findById(id).orElse(null);
        if (c == null) return error(HttpStatus.NOT_FOUND, "Contest not found.");
        Instant now = clock.instant();
        if (now.isBefore(c.getEndsAt().plus(GRACE))) {
            return error(HttpStatus.FORBIDDEN, "Results are published when the contest ends.");
        }
        User viewer = viewer(principal);
        List<ContestEntry> ranked = entries.submitted(c.getId()).stream()
                .sorted(Comparator.comparingDouble(ContestEntry::getScore).reversed()
                        .thenComparingInt(ContestEntry::getSecondsUsed)
                        .thenComparing(ContestEntry::getSubmittedAt))
                .toList();
        List<ResultRow> rows = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            ContestEntry e = ranked.get(i);
            // Equal scores and times share a rank.
            int rank = i > 0 && same(ranked.get(i - 1), e) ? rows.get(i - 1).rank() : i + 1;
            rows.add(new ResultRow(rank, LeaderboardController.name(e.getUser()), e.getScore(), e.getCorrect(),
                    e.getWrong(), e.getSkipped(), e.getSecondsUsed(),
                    viewer != null && e.getUser().getId().equals(viewer.getId())));
        }
        ResultRow you = rows.stream().filter(ResultRow::you).findFirst().orElse(null);
        Double percentile = you == null ? null : percentile(rows, you);
        return ResponseEntity.ok(new Results(view(c, entry(c, viewer), now), rows.size(),
                rows.subList(0, Math.min(100, rows.size())), you, percentile));
    }

    /** Share of participants who scored strictly below you, plus half of those tied, as a percentage. */
    static double percentile(List<ResultRow> rows, ResultRow you) {
        long below = rows.stream().filter(r -> r.score() < you.score()).count();
        long tied = rows.stream().filter(r -> r.score() == you.score()).count() - 1;
        double p = rows.size() <= 1 ? 100 : 100.0 * (below + tied / 2.0) / (rows.size() - 1);
        return Math.round(p * 10) / 10.0;
    }

    private static boolean same(ContestEntry a, ContestEntry b) {
        return a.getScore().equals(b.getScore()) && a.getSecondsUsed().equals(b.getSecondsUsed());
    }

    // ---------------------------------------------------------------- admin

    @PostMapping("/admin/contests")
    @Transactional
    public ResponseEntity<?> create(@Valid @RequestBody ContestRequest req) {
        Quiz quiz = catalog.find(req.quizKey());
        if (quiz == null) return error(HttpStatus.BAD_REQUEST, "No such exam quiz or mock.");
        Contest c = new Contest();
        c.setTitle(req.title().trim());
        c.setQuizKey(quiz.key());
        c.setStartsAt(req.startsAt());
        c.setMinutes(req.minutes() > 0 ? req.minutes() : quiz.minutes());
        contests.save(c);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(c, null, clock.instant()));
    }

    @DeleteMapping("/admin/contests/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!contests.existsById(id)) return ResponseEntity.notFound().build();
        contests.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/contests/quizzes")
    public List<String> quizzes() {
        return catalog.keys();
    }

    // ---------------------------------------------------------------- helpers

    /** Each entrant's own clock (full time from opening the paper), capped at the contest's end. */
    private static Instant deadline(Contest c, ContestEntry e) {
        Instant own = e.getStartedAt().plus(Duration.ofMinutes(c.getMinutes()));
        return own.isBefore(c.getEndsAt()) ? own : c.getEndsAt();
    }

    /** questions[n] = paper index shown at position n; options[q][k] = original option shown at position k. */
    private record Order(int[] questions, int[][] options) {}

    private static Order order(Contest c, User u, List<Question> qs) {
        Random random = new Random(31L * c.getId() + u.getId() * 1_000_003L);
        List<Integer> q = new ArrayList<>();
        for (int i = 0; i < qs.size(); i++) q.add(i);
        // Shuffle within each section, so a mock keeps its sections in order. Questions of a DI table, puzzle or
        // passage stay where they are, together and in order; only the stand-alone ones swap places.
        int from = 0;
        for (int i = 1; i <= qs.size(); i++) {
            if (i == qs.size() || !qs.get(i).section().equals(qs.get(from).section())) {
                List<Integer> slots = new ArrayList<>();
                for (int k = from; k < i; k++) if (qs.get(k).group() == null) slots.add(k);
                List<Integer> picks = new ArrayList<>(slots);
                Collections.shuffle(picks, random);
                for (int k = 0; k < slots.size(); k++) q.set(slots.get(k), picks.get(k));
                from = i;
            }
        }
        int[][] options = new int[qs.size()][];
        for (int i = 0; i < qs.size(); i++) {
            List<Integer> o = new ArrayList<>();
            for (int k = 0; k < qs.get(i).options().size(); k++) o.add(k);
            if (!fixedOrder(qs.get(i).options())) Collections.shuffle(o, random);
            options[i] = o.stream().mapToInt(Integer::intValue).toArray();
        }
        return new Order(q.stream().mapToInt(Integer::intValue).toArray(), options);
    }

    /** Same idea as build_exams.py's is_fixed_order: options whose position carries meaning stay put. */
    static boolean fixedOrder(List<String> options) {
        for (String o : options) {
            for (String m : List.of("No error", "No improvement", "Part (", "conclusion", "None of", "All of", "Both ")) {
                if (o.contains(m)) return true;
            }
        }
        return options.stream().allMatch(o -> !o.isEmpty() && o.length() <= 5 && o.chars().allMatch(Character::isUpperCase));
    }

    private ContestView view(Contest c, ContestEntry e, Instant now) {
        Quiz quiz = catalog.find(c.getQuizKey());
        String state = now.isBefore(c.getStartsAt()) ? "upcoming" : now.isBefore(c.getEndsAt()) ? "live" : "finished";
        String you = e == null ? null : e.getSubmittedAt() != null ? "submitted" : e.getStartedAt() != null ? "started" : "registered";
        return new ContestView(c.getId(), c.getTitle(), c.getQuizKey(), c.getStartsAt(), c.getEndsAt(), c.getMinutes(),
                quiz == null ? 0 : quiz.questionCount(), quiz == null ? 0 : quiz.maxMarks(), state,
                entries.countByContestId(c.getId()), you);
    }

    private ContestEntry entry(Contest c, User viewer) {
        return viewer == null ? null : entries.findByContestIdAndUserId(c.getId(), viewer.getId()).orElse(null);
    }

    private User viewer(Principal principal) {
        return principal == null ? null : users.findByUsername(principal.getName()).orElse(null);
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
