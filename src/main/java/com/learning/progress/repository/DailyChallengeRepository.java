package com.learning.progress.repository;

import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.entity.ClassLesson;
import com.learning.progress.entity.DailyChallenge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;

public interface DailyChallengeRepository extends JpaRepository<DailyChallenge, Long> {


    Optional<DailyChallenge> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
        SELECT dc FROM DailyChallenge dc
        LEFT JOIN FETCH dc.classLesson cl
        LEFT JOIN FETCH cl.classChapter cc
        LEFT JOIN FETCH cc.clazz c
        LEFT JOIN FETCH c.syllabus s
        LEFT JOIN FETCH s.level l
        WHERE dc.id = :id AND dc.deletedAt IS NULL
        """)
    Optional<DailyChallenge> findByIdWithFullHierarchy(@Param("id") Long id);


    @Query("""
    SELECT cl
    FROM ClassLesson cl
    JOIN cl.classChapter cc
    JOIN cc.clazz c
    LEFT JOIN cl.dailyChallenges dc WITH dc.deletedAt IS NULL
    WHERE c.id = :classId
      AND cl.deletedAt IS NULL
      AND (
            :text IS NULL OR :text = '' OR
            LOWER(dc.challengeName) LIKE LOWER(CONCAT('%', :text, '%')) OR
            LOWER(cl.classLessonName) LIKE LOWER(CONCAT('%', :text, '%')) OR
            LOWER(dc.description) LIKE LOWER(CONCAT('%', :text, '%'))
      )
      AND (
            :isTeacher = TRUE
            OR dc.challengeStatus != 'DRAFT'
            OR dc IS NULL
      )
    GROUP BY cl.id, cc.id
    ORDER BY cc.id ASC, cl.orderNumber ASC
    """)
    Page<ClassLesson> findLessonsWithChallengesByClassId(
            @Param("classId") Long classId,
            @Param("text") String text,
            @Param("isTeacher") boolean isTeacher,
            Pageable pageable
    );

    boolean existsByClassLessonAndChallengeNameAndDeletedAtIsNull(ClassLesson classLesson, String challengeName);

    boolean existsByClassLessonAndChallengeNameAndDeletedAtIsNullAndIdNot(ClassLesson classLesson, String challengeName, Long id);

    // New: fetch all challenges for multiple lessons in one query to avoid N+1
    List<DailyChallenge> findByClassLessonIdInAndDeletedAtIsNull(List<Long> lessonIds);

    /**
     * Sum of question.weight for a given daily challenge (max possible weight).
     * Returns null if no questions found.
     *
     * Note: question -> section -> challenge relationship used (q.section.challenge).
     */
    @Query("""
        SELECT SUM(q.weight)
        FROM Question q
        WHERE q.section.challenge.id = :challengeId
          AND q.deletedAt IS NULL
        """)
    BigDecimal sumQuestionWeightByChallengeId(@Param("challengeId") Long challengeId);

    @Query("""
    SELECT dc
    FROM DailyChallenge dc
    JOIN dc.classLesson cl
    JOIN cl.classChapter cc
    JOIN cc.clazz c
    WHERE c.id = :classId
      AND dc.deletedAt IS NULL
      AND dc.challengeStatus <> 'DRAFT'
    """)
    List<DailyChallenge> findNonDraftByClassId(@Param("classId") Long classId);

}