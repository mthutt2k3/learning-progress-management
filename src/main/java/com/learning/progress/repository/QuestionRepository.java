package com.learning.progress.repository;

import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findBySectionIdAndDeletedAtIsNull(Long sectionId);

    Optional<Question> findByIdAndDeletedAtIsNull(Long id);

    List<Question> findBySectionIdAndDeletedAtIsNullOrderByOrderNumberAsc(Long sectionId);

    List<Question> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    @Query("SELECT q FROM Question q " +
            "JOIN q.section s " +
            "JOIN s.challenge c " +
            "WHERE c.id = :challengeId AND q.deletedAt IS NULL")
    List<Question> findByChallengeIdAndDeletedAtIsNull(@Param("challengeId") Long challengeId);

    List<Question> findByIdInAndDeletedAtIsNull(List<Long> questionIds);

    @Query("""
    SELECT COUNT(q)
    FROM Question q
    JOIN ChallengeSection s ON q.section.id = s.id
    WHERE s.challenge.id = :challengeId
      AND q.deletedAt IS NULL
      AND s.deletedAt IS NULL
""")
    long countByChallengeIdAndDeletedAtIsNull(@Param("challengeId") Long challengeId);

    /**
     * Sum question weight grouped by challenge id for a list of challengeIds.
     * Returns list of Object[] where index 0 = challengeId (Long) and index 1 = sum(weight) (BigDecimal).
     */
    @Query("SELECT q.section.challenge.id, SUM(q.weight) FROM Question q WHERE q.section.challenge.id IN :challengeIds AND q.deletedAt IS NULL GROUP BY q.section.challenge.id")
    List<Object[]> sumWeightByChallengeIds(@Param("challengeIds") List<Long> challengeIds);

}