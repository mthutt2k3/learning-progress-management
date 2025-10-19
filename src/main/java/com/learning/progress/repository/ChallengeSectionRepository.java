package com.learning.progress.repository;

import com.learning.progress.entity.ChallengeSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChallengeSectionRepository extends JpaRepository<ChallengeSection, Long> {

    @Query("SELECT cs FROM ChallengeSection cs WHERE cs.challenge.id = :challengeId AND cs.deletedAt IS NULL")
    List<ChallengeSection> findByChallengeId(@Param("challengeId") Long challengeId);
}