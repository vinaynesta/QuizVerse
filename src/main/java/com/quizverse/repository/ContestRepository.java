package com.quizverse.repository;

import com.quizverse.model.Contest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ContestRepository extends JpaRepository<Contest, Long> {
    /** Contests starting after {@code from}, soonest first (upcoming and live ones are filtered by the caller). */
    List<Contest> findByStartsAtAfterOrderByStartsAtAsc(Instant from);
}
