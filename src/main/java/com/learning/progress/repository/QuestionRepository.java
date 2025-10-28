package com.learning.progress.repository;

import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @Query("SELECT q FROM Question q WHERE q.section.challenge.id = :challengeId AND q.deletedAt IS NULL")
    List<Question> findByChallengeId(@Param("challengeId") Long challengeId);

    @Query("SELECT q FROM Question q WHERE q.section.id = :sectionId AND q.deletedAt IS NULL")
    List<Question> findBySectionId(@Param("sectionId") Long sectionId);

    List<Question> findBySectionIdAndDeletedAtIsNull(Long sectionId);

    Optional<Question> findByIdAndDeletedAtIsNull(Long id);

    List<Question> findBySectionIdAndDeletedAtIsNullOrderByOrderNumberAsc(Long sectionId);

    boolean existsBySectionAndQuestionTextIgnoreCaseAndDeletedAtIsNull(ChallengeSection section, String trimmedText);

    List<Question> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    @Query("SELECT q FROM Question q " +
            "JOIN q.section s " +
            "JOIN s.challenge c " +
            "WHERE c.id = :challengeId AND q.deletedAt IS NULL")
    List<Question> findByChallengeIdAndDeletedAtIsNull(@Param("challengeId") Long challengeId);

    List<Question> findByIdInAndDeletedAtIsNull(List<Long> questionIds);
}