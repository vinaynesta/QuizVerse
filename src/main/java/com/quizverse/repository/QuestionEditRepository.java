package com.quizverse.repository;

import com.quizverse.model.QuestionEdit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionEditRepository extends JpaRepository<QuestionEdit, Long> {
    Optional<QuestionEdit> findByQuestionId(String questionId);
    List<QuestionEdit> findAllByOrderByUpdatedAtDesc();
}
