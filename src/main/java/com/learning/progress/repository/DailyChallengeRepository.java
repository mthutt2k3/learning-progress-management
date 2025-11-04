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

    // New JPA helper: fetch challenges for a given lesson (used to assemble DTOs in service)
    List<DailyChallenge> findByClassLessonAndDeletedAtIsNull(ClassLesson classLesson);

    boolean existsByClassLessonAndChallengeNameAndDeletedAtIsNullAndIdNot(ClassLesson classLesson, String challengeName, Long id);

    // New: fetch all challenges for multiple lessons in one query to avoid N+1
    List<DailyChallenge> findByClassLessonIdInAndDeletedAtIsNull(List<Long> lessonIds);
}