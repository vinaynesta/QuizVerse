package com.quizverse.mail;

import com.quizverse.model.QuizAttempt;
import com.quizverse.model.User;
import com.quizverse.repository.QuizAttemptRepository;
import com.quizverse.repository.UserRepository;
import com.quizverse.service.Streaks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Opt-in emails: a reminder after 3 days without a quiz (once per quiet spell, not every day) and a Monday
 * summary of the past week (skipped when the week had no quizzes). Times are IST. Every email has a one-click
 * unsubscribe link and List-Unsubscribe headers, which Gmail and Yahoo require for bulk mail.
 * <p>
 * The last_* columns stop repeats after a redeploy. Runs on one server only: a second instance would send
 * duplicates (see SessionTracker for the same limit).
 */
@Service
public class EmailDigests {

    private static final Logger log = LoggerFactory.getLogger(EmailDigests.class);
    static final Duration QUIET = Duration.ofDays(3);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final QuizAttemptRepository attempts;
    private final Mailer mailer;
    private final Clock clock;
    private final String publicUrl;

    public EmailDigests(UserRepository users, QuizAttemptRepository attempts, Mailer mailer, Clock clock,
                        @Value("${quizverse.public-url:}") String publicUrl) {
        this.users = users;
        this.attempts = attempts;
        this.mailer = mailer;
        this.clock = clock;
        this.publicUrl = publicUrl.trim().replaceAll("/+$", "");
    }

    /** Every evening at 6:15 p.m. IST, when people are free to practise. */
    @Scheduled(cron = "0 15 18 * * *", zone = "Asia/Kolkata")
    public void sendReminders() {
        if (!ready()) return;
        Instant now = clock.instant();
        int sent = 0;
        for (User user : users.findByEmailRemindersTrue()) {
            Instant last = attempts.lastCompletion(user.getId());
            Instant active = last == null || last.isBefore(user.getCreatedAt()) ? user.getCreatedAt() : last;
            if (active.isAfter(now.minus(QUIET))) continue;
            // One reminder per quiet spell: none since the last quiz.
            if (user.getLastReminderAt() != null && user.getLastReminderAt().isAfter(active)) continue;
            long days = Duration.between(active, now).toDays();
            String text = """
                    Hi %s,

                    It's been %d days since your last QuizVerse quiz. A short quiz today keeps your preparation going:
                    today's daily quiz takes about 10 minutes.

                    %s/

                    """.formatted(user.getDisplayName(), days, publicUrl);
            if (send(user, "A quick quiz today?", text)) {
                user.setLastReminderAt(now);
                users.save(user);
                sent++;
            }
        }
        if (sent > 0) log.info("Sent {} practice reminders", sent);
    }

    /** Mondays at 8:10 a.m. IST, covering the previous seven days. */
    @Scheduled(cron = "0 10 8 * * MON", zone = "Asia/Kolkata")
    public void sendWeeklySummaries() {
        if (!ready()) return;
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(Streaks.ZONE));
        Instant since = today.minusDays(7).atStartOfDay(Streaks.ZONE).toInstant();
        int sent = 0;
        for (User user : users.findByEmailWeeklyTrue()) {
            if (user.getLastWeeklyAt() != null && user.getLastWeeklyAt().isAfter(now.minus(Duration.ofDays(6)))) continue;
            List<QuizAttempt> week = attempts.findByUserIdAndCompletedAtAfter(user.getId(), since);
            if (week.isEmpty()) continue;
            Streaks.Summary streak = Streaks.of(attempts.completionTimes(user.getId()), today);
            if (send(user, "Your QuizVerse week: " + week.size() + (week.size() == 1 ? " quiz" : " quizzes"),
                    summary(user, week, streak))) {
                user.setLastWeeklyAt(now);
                users.save(user);
                sent++;
            }
        }
        if (sent > 0) log.info("Sent {} weekly summaries", sent);
    }

    static String summary(User user, List<QuizAttempt> week, Streaks.Summary streak) {
        double scored = week.stream().mapToDouble(a -> Math.max(0, a.getScore())).sum();
        double max = week.stream().mapToDouble(QuizAttempt::getMaxMarks).sum();
        long days = week.stream().map(a -> Streaks.day(a.getCompletedAt())).distinct().count();
        QuizAttempt best = week.stream()
                .filter(a -> a.getMaxMarks() > 0)
                .max((a, b) -> Double.compare(a.getScore() / a.getMaxMarks(), b.getScore() / b.getMaxMarks()))
                .orElse(week.get(0));
        return """
                Hi %s,

                Here's your last week on QuizVerse:

                  Quizzes finished:   %d (on %d of 7 days)
                  Average score:      %d%%
                  Best result:        %s (%s / %s)
                  Current streak:     %d %s (best ever: %d)

                Your weak topics and full history are on My scores.

                """.formatted(user.getDisplayName(), week.size(), days,
                Math.round(max > 0 ? 100 * scored / max : 0), best.getQuizTitle(), marks(best.getScore()),
                marks(best.getMaxMarks()), streak.current(), streak.current() == 1 ? "day" : "days", streak.best());
    }

    private static String marks(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** Off without a provider, and in production without the public address the links need. */
    private boolean ready() {
        return mailer.isEnabled() && !publicUrl.isEmpty();
    }

    private boolean send(User user, String subject, String body) {
        if (user.getEmailToken() == null) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            user.setEmailToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            users.save(user);
        }
        String page = publicUrl + "/unsubscribe.html?token=" + user.getEmailToken();
        String oneClick = publicUrl + "/api/email/unsubscribe?token=" + user.getEmailToken();
        String text = body + """
                You get this because you turned on QuizVerse emails on your profile page.
                Unsubscribe from all QuizVerse emails: %s
                """.formatted(page);
        return mailer.send(user.getEmail(), subject, text, Map.of(
                "List-Unsubscribe", "<" + oneClick + ">",
                "List-Unsubscribe-Post", "List-Unsubscribe=One-Click"));
    }
}
