package com.quizverse.repository;

import com.quizverse.model.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByUserIdOrderByCompletedAtDesc(Long userId);

    /** When each of the user's quizzes was finished, for streaks. */
    @Query("select a.completedAt from QuizAttempt a where a.user.id = :userId")
    List<Instant> completionTimes(@Param("userId") Long userId);

    /** The daily quiz stores its IST date (yyyy-mm-dd) in sectionId. */
    boolean existsByUserIdAndKindAndSectionId(Long userId, String kind, String sectionId);

    /** [examId, stageId, sectionId, levelId, quizIndex, best score, max marks, attempts] for exam quizzes and mocks. */
    @Query("""
            select a.examId, a.stageId, a.sectionId, a.levelId, a.quizIndex, max(a.score), max(a.maxMarks), count(a)
            from QuizAttempt a
            where a.user.id = :userId and a.kind in ('exam', 'mock')
            group by a.examId, a.stageId, a.sectionId, a.levelId, a.quizIndex""")
    List<Object[]> bestScores(@Param("userId") Long userId);
}
