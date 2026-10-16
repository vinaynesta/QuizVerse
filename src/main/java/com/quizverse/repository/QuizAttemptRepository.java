package com.quizverse.repository;

import com.quizverse.model.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByUserIdOrderByCompletedAtDesc(Long userId);

    /** [examId, stageId, sectionId, levelId, quizIndex, best score, max marks, attempts] for exam quizzes. */
    @Query("""
            select a.examId, a.stageId, a.sectionId, a.levelId, a.quizIndex, max(a.score), max(a.maxMarks), count(a)
            from QuizAttempt a
            where a.user.id = :userId and a.kind = 'exam'
            group by a.examId, a.stageId, a.sectionId, a.levelId, a.quizIndex""")
    List<Object[]> bestScores(@Param("userId") Long userId);
}
