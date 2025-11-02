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

public interface DailyChallengeRepository extends JpaRepository<DailyChallenge, Long> {


    Optional<DailyChallenge> findByIdAndDeletedAtIsNull(Long id);


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
            OR dc.challengeStatus = 'PUBLISHED'
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

    @Query("""
    SELECT cl
    FROM ClassLesson cl
    JOIN cl.classChapter cc
    JOIN cc.clazz c
    LEFT JOIN cl.publishedDailyChallenges dc
    LEFT JOIN dc.submissionDailyChallenges sdc WITH 
          sdc.user.id = :studentId 
      AND sdc.deletedAt IS NULL
    WHERE c.id = :classId
      AND cl.deletedAt IS NULL
      AND (
            :text IS NULL OR :text = '' OR
            LOWER(cl.classLessonName) LIKE LOWER(CONCAT('%', :text, '%')) OR
            (dc IS NOT NULL AND (
                LOWER(dc.challengeName) LIKE LOWER(CONCAT('%', :text, '%')) OR
                LOWER(dc.description) LIKE LOWER(CONCAT('%', :text, '%'))
            ))
      )
    GROUP BY cl.id, cc.id
    ORDER BY cc.orderNumber ASC, cl.orderNumber ASC
    """)
    Page<ClassLesson> findAllLessonsWithPublishedChallengesAndSubmissions(
            @Param("classId") Long classId,
            @Param("studentId") Long studentId,
            @Param("text") String text,
            Pageable pageable
    );
    @Query(value = """
    WITH lesson_list AS (
        SELECT 
            cl.id AS lesson_id,
            cl.class_lesson_name,
            cl.class_lesson_content,
            cl.order_number,
            cc.order_number AS chapter_order,
            ROW_NUMBER() OVER (ORDER BY cc.order_number, cl.order_number) AS rn
        FROM class_lessons cl
        JOIN class_chapters cc ON cc.id = cl.class_chapter_id
        JOIN classes c ON c.id = cc.class_id
        WHERE c.id = :classId
          AND cl.deleted_at IS NULL
          AND (
              :text IS NULL OR :text = '' OR
              LOWER(cl.class_lesson_name) LIKE LOWER('%' || :text || '%')
          )
    ),
    challenge_data AS (
        SELECT 
            dc.id,
            dc.challenge_name,
            dc.challenge_type,
            'PUBLISHED' AS challenge_status,
            sdc.id AS submission_id,
            sdc.started_at,
            sdc.expired_at,
            sdc.submission_status,
            sdc.is_late,
            sdc.submitted_at,
            gdc.total_score,
            gdc.score_percentage,
            dc.class_lesson_id
        FROM daily_challenges dc
        LEFT JOIN submission_daily_challenges sdc 
          ON sdc.challenge_id = dc.id 
          AND sdc.user_id = :studentId 
          AND sdc.deleted_at IS NULL
        LEFT JOIN grading_daily_challenges gdc 
          ON gdc.submission_daily_id = sdc.id 
          AND gdc.is_finalized = TRUE 
          AND gdc.deleted_at IS NULL
        WHERE dc.class_lesson_id IN (SELECT lesson_id FROM lesson_list)
          AND dc.deleted_at IS NULL
          AND dc.status = 'PUBLISHED'
          AND (
              :text IS NULL OR :text = '' OR
              LOWER(dc.challenge_name) LIKE LOWER('%' || :text || '%') OR
              LOWER(dc.description) LIKE LOWER('%' || :text || '%')
          )
    ),
    lesson_with_challenges AS (
        SELECT 
            l.*,
            jsonb_agg(
                jsonb_build_object(
                    'id', c.id,
                    'challengeName', c.challenge_name,
                    'challengeType', c.challenge_type,
                    'challengeStatus', c.challenge_status,
                    'submissionChallengeId', c.submission_id,
                    'startDate', c.started_at,
                    'endDate', c.expired_at,
                    'submissionStatus', c.submission_status,
                    'isLate', c.is_late,
                    'submittedAt', c.submitted_at,
                    'totalScore', c.total_score,
                    'scorePercentage', c.score_percentage
                ) ORDER BY c.started_at
            ) FILTER (WHERE c.id IS NOT NULL) AS challenges_json
        FROM lesson_list l
        LEFT JOIN challenge_data c ON c.class_lesson_id = l.lesson_id
        GROUP BY l.lesson_id, l.class_lesson_name, l.class_lesson_content, l.order_number, l.chapter_order, l.rn
    )
    SELECT 
        lesson_id,
        class_lesson_name,
        class_lesson_content,
        order_number,
        COALESCE(challenges_json, '[]'::jsonb) AS challenges
    FROM lesson_with_challenges
    WHERE rn > :offset AND rn <= :offset + :size
    ORDER BY chapter_order, order_number
    """,
            countQuery = """
    SELECT COUNT(*) 
    FROM class_lessons cl
    JOIN class_chapters cc ON cc.id = cl.class_chapter_id
    WHERE cc.class_id = :classId AND cl.deleted_at IS NULL
    """,
            nativeQuery = true
    )
    Page<Object[]> findStudentChallengesNative(
            @Param("classId") Long classId,
            @Param("studentId") Long studentId,
            @Param("text") String text,
            @Param("offset") Long offset,
            @Param("size") Integer size,
            Pageable pageable
    );

}