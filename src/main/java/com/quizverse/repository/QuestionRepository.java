package com.quizmailer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.quizmailer.model.Question;
import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    @Query(value = "SELECT * FROM question ORDER BY RAND() LIMIT 5", nativeQuery = true)
    List<Question> getRandomFiveQuestions();
}
