package com.learning.progress.repository;

import com.learning.progress.entity.ChallengeSection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChallengeSectionRepository extends JpaRepository<ChallengeSection, Long> {

    /**
     * Find a ChallengeSection by ID where deletedAt is null.
     *
     * @param id The ID of the ChallengeSection.
     * @return Optional containing the ChallengeSection if found and not deleted.
     */
    Optional<ChallengeSection> findByIdAndDeletedAtIsNull(Long id);

    /**
     * Find ChallengeSections by challenge ID with soft deletion check.
     *
     * @param challengeId The ID of the DailyChallenge to filter by.
     * @param pageable    Pagination and sorting information.
     * @return A Page of ChallengeSections for the specified challenge that are not deleted.
     */
    @Query("SELECT s FROM ChallengeSection s WHERE s.challenge.id = :challengeId AND s.deletedAt IS NULL order by s.orderNumber asc ")
    Page<ChallengeSection> findByChallengeIdAndDeletedAtIsNull(@Param("challengeId") Long challengeId, Pageable pageable);

    /**
     * Find ChallengeSections by challenge ID and text search in sectionTitle or sectionsContent, with soft deletion check.
     *
     * @param challengeId The ID of the DailyChallenge to filter by.
     * @param text        The search text to match against sectionTitle or sectionsContent.
     * @param pageable    Pagination and sorting information.
     * @return A Page of ChallengeSections matching the criteria.
     */
    @Query("SELECT s FROM ChallengeSection s WHERE s.challenge.id = :challengeId AND s.deletedAt IS NULL " +
            "AND (:text IS NULL OR LOWER(s.sectionTitle) LIKE LOWER(CONCAT('%', :text, '%')) " +
            "OR LOWER(s.sectionsContent) LIKE LOWER(CONCAT('%', :text, '%'))) order by s.orderNumber asc ")
    Page<ChallengeSection> findByChallengeIdAndTextAndDeletedAtIsNull(@Param("challengeId") Long challengeId, @Param("text") String text, Pageable pageable);
}