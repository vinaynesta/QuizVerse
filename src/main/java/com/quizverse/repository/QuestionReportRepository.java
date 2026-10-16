package com.quizverse.repository;

import com.quizverse.model.QuestionReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuestionReportRepository extends JpaRepository<QuestionReport, Long> {
    boolean existsByUserIdAndQuestionIdAndResolvedFalse(Long userId, String questionId);

    @Query("select r from QuestionReport r join fetch r.user where r.resolved = false order by r.createdAt desc")
    List<QuestionReport> findOpen();
}
