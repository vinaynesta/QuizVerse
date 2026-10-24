package com.quizverse.repository;

import com.quizverse.model.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {
    Optional<Challenge> findByCode(String code);
    Optional<Challenge> findByAttemptId(Long attemptId);
    boolean existsByCode(String code);
}
