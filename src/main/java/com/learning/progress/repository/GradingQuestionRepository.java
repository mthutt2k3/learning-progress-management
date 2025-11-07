package com.learning.progress.repository;

import com.learning.progress.entity.GradingQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
public interface GradingQuestionRepository extends JpaRepository<GradingQuestion, Long> {
    Optional<GradingQuestion> findBySubmissionQuestionIdAndDeletedAtIsNull(Long submissionQuestionId);
    List<GradingQuestion> findBySubmissionQuestion_SubmissionDaily_IdAndDeletedAtIsNull(Long submissionDailyId);

    List<GradingQuestion> findByGradingDailyIdAndDeletedAtIsNull(Long id);

    // NEW: batch load grading questions for multiple gradingDaily ids to avoid N+1
    List<GradingQuestion> findByGradingDailyIdInAndDeletedAtIsNull(List<Long> gradingDailyIds);

    /**
     * Grouped sum of receivedWeight for a list of gradingDaily ids.
     * Returns list of [gradingDailyId, sumReceivedWeight] rows.
     */
    @Query("""
        SELECT gq.gradingDaily.id, SUM(gq.receivedWeight)
        FROM GradingQuestion gq
        WHERE gq.gradingDaily.id IN :gradingIds
          AND gq.deletedAt IS NULL
        GROUP BY gq.gradingDaily.id
        """)
    List<Object[]> sumReceivedWeightGroupByGradingIds(@Param("gradingIds") List<Long> gradingIds);

    /**
     * Convenience default method that returns a Map<gradingDailyId, sumReceivedWeight>.
     * Uses the grouped query above and converts results to a typed map.
     */
    default Map<Long, Double> sumReceivedWeightMapByGradingIds(List<Long> gradingIds) {
        if (gradingIds == null || gradingIds.isEmpty()) return Collections.emptyMap();
        List<Object[]> rows = sumReceivedWeightGroupByGradingIds(gradingIds);
        if (rows == null || rows.isEmpty()) return Collections.emptyMap();

        return rows.stream().collect(Collectors.toMap(
                r -> ((Number) r[0]).longValue(),
                r -> {
                    Number n = (Number) r[1];
                    return n == null ? 0.0 : n.doubleValue();
                }
        ));
    }
}
