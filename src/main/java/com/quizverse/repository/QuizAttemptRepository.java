package com.quizverse.repository;

import com.quizverse.model.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByUserIdOrderByCompletedAtDesc(Long userId);

    /** Friends' attempts through a challenge link, oldest first. */
    @Query("select a from QuizAttempt a join fetch a.user where a.challenge.id = :challengeId order by a.completedAt, a.id")
    List<QuizAttempt> challengeEntries(@Param("challengeId") Long challengeId);

    Optional<QuizAttempt> findByIdAndUserId(Long id, Long userId);

    /** When each of the user's quizzes was finished, for streaks. */
    @Query("select a.completedAt from QuizAttempt a where a.user.id = :userId")
    List<Instant> completionTimes(@Param("userId") Long userId);

    /** When the user last finished a quiz, or null. */
    @Query("select max(a.completedAt) from QuizAttempt a where a.user.id = :userId")
    Instant lastCompletion(@Param("userId") Long userId);

    /** The user's quizzes finished since {@code since}, for the weekly summary. */
    List<QuizAttempt> findByUserIdAndCompletedAtAfter(Long userId, Instant since);

    boolean existsByReceiptHash(String receiptHash);

    /** Verified attempts at one quiz (leaderboard), with their users. */
    @Query("""
            select a from QuizAttempt a join fetch a.user
            where a.verified = true and a.kind = :kind and a.examId = :examId and a.stageId = :stageId
              and a.sectionId = :sectionId and a.levelId = :levelId and a.quizIndex = :quizIndex""")
    List<QuizAttempt> verifiedFor(@Param("kind") String kind, @Param("examId") String examId,
                                  @Param("stageId") String stageId, @Param("sectionId") String sectionId,
                                  @Param("levelId") String levelId, @Param("quizIndex") int quizIndex);

    /** Verified exam quizzes and mocks finished since {@code since} (weekly leaderboard), with their users. */
    @Query("""
            select a from QuizAttempt a join fetch a.user
            where a.verified = true and a.kind in ('exam', 'mock') and a.completedAt >= :since""")
    List<QuizAttempt> verifiedSince(@Param("since") Instant since);

    /** The daily quiz and the daily tech challenge store their IST date (yyyy-mm-dd) in sectionId. */
    boolean existsByUserIdAndKindAndSectionId(Long userId, String kind, String sectionId);

    /** [examId, stageId, sectionId, levelId, quizIndex, best score, max marks, attempts] for exam quizzes and mocks. */
    @Query("""
            select a.examId, a.stageId, a.sectionId, a.levelId, a.quizIndex, max(a.score), max(a.maxMarks), count(a)
            from QuizAttempt a
            where a.user.id = :userId and a.kind in ('exam', 'mock')
            group by a.examId, a.stageId, a.sectionId, a.levelId, a.quizIndex""")
    List<Object[]> bestScores(@Param("userId") Long userId);
}
