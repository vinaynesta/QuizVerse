package com.quizverse.repository;

import com.quizverse.model.ContestEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContestEntryRepository extends JpaRepository<ContestEntry, Long> {
    Optional<ContestEntry> findByContestIdAndUserId(Long contestId, Long userId);

    long countByContestId(Long contestId);

    /** Submitted entries with their users, for ranking. */
    @Query("select e from ContestEntry e join fetch e.user where e.contest.id = :contestId and e.submittedAt is not null")
    List<ContestEntry> submitted(@Param("contestId") Long contestId);

    @Query("select e.contest.id from ContestEntry e where e.user.id = :userId")
    List<Long> contestIdsFor(@Param("userId") Long userId);
}
