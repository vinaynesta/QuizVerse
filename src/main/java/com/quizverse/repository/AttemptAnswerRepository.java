package com.quizverse.repository;

import com.quizverse.model.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    /** Per subject and topic: [subject, topic, answered, correct, skipped, total seconds, answered seconds]. */
    @Query("""
            select a.subject, a.topic,
                   sum(case when a.chosen is not null then 1 else 0 end),
                   sum(case when a.correct = true then 1 else 0 end),
                   sum(case when a.chosen is null then 1 else 0 end),
                   sum(a.seconds),
                   sum(case when a.chosen is not null then a.seconds else 0 end)
            from AttemptAnswer a
            where a.attempt.user.id = :userId
            group by a.subject, a.topic""")
    List<Object[]> topicStats(@Param("userId") Long userId);

    /**
     * Questions whose most recent answer was wrong or skipped: answering one correctly takes it off the list.
     * Rows are [questionId, subject, topic, lastChosen, lastCorrect, lastAnsweredAt], newest first.
     */
    @Query("""
            select a.questionId, a.subject, a.topic, a.chosen, a.correct, a.attempt.completedAt
            from AttemptAnswer a
            where a.attempt.user.id = :userId
              and a.id = (select max(b.id) from AttemptAnswer b
                          where b.attempt.user.id = :userId and b.questionId = a.questionId)
              and a.correct = false
            order by a.attempt.completedAt desc""")
    List<Object[]> lastAnswerWrong(@Param("userId") Long userId);

    /** Times this user got each question wrong (skips excluded), for "missed 3 times". */
    @Query("""
            select a.questionId, count(a)
            from AttemptAnswer a
            where a.attempt.user.id = :userId and a.correct = false and a.chosen is not null
            group by a.questionId""")
    List<Object[]> wrongCounts(@Param("userId") Long userId);
}
