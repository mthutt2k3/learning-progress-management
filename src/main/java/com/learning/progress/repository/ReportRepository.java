package com.learning.progress.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public interface ReportRepository extends JpaRepository<com.learning.progress.entity.DailyChallenge, Long> {

    /* --------------------------------------------------------
     * CLASS REPORT QUERIES
     * -------------------------------------------------------- */

    /**
     * FIXED: Get average score using grading_daily_challenges
     */
    @Query(value = """
        SELECT COALESCE(AVG(gdc.raw_score * (1 - gdc.penalty_applied)), 0) as avgScore
        FROM grading_daily_challenges gdc
        JOIN submission_daily_challenges sdc ON gdc.submission_daily_id = sdc.id
        JOIN daily_challenges dc ON sdc.challenge_id = dc.id
        JOIN class_lessons cl ON dc.class_lesson_id = cl.id
        JOIN class_chapters cc ON cl.class_chapter_id = cc.id
        WHERE cc.class_id = :classId
        AND dc.deleted_at IS NULL
        AND sdc.deleted_at IS NULL
        AND sdc.submission_status IN ('SUBMITTED', 'GRADED')
        AND gdc.deleted_at IS NULL
        """, nativeQuery = true)
    BigDecimal getAverageScoreByClass(@Param("classId") Long classId);

    @Query(value = """
        SELECT 
            COUNT(CASE WHEN dc.status = 'FINISHED' THEN 1 END)::decimal / 
            NULLIF(COUNT(*), 0) * 100 as completionRate
        FROM daily_challenges dc
        JOIN class_lessons cl ON dc.class_lesson_id = cl.id
        JOIN class_chapters cc ON cl.class_chapter_id = cc.id
        WHERE cc.class_id = :classId
        AND dc.deleted_at IS NULL
        """, nativeQuery = true)
    BigDecimal getCompletionRateByClass(@Param("classId") Long classId);

    @Query(value = """
        SELECT 
            COUNT(*) as total_lessons,
            COUNT(CASE WHEN EXISTS (
                SELECT 1 FROM daily_challenges dc 
                WHERE dc.class_lesson_id = cl.id 
                AND dc.status = 'FINISHED'
                AND dc.deleted_at IS NULL
            ) THEN 1 END) as completed_lessons
        FROM class_lessons cl
        JOIN class_chapters cc ON cl.class_chapter_id = cc.id
        WHERE cc.class_id = :classId
        AND cl.deleted_at IS NULL
        """, nativeQuery = true)
    Map<String, Object> getLessonStatsByClass(@Param("classId") Long classId);

    @Query(value = """
        SELECT 'TEACHER' as role_name, COUNT(DISTINCT ct.user_id) as count
        FROM class_teachers ct
        JOIN users u ON ct.user_id = u.id
        WHERE ct.class_id = :classId 
        AND ct.role_in_class = 'TEACHER'
        AND ct.status = 'ACTIVE'
        AND ct.deleted_at IS NULL
        AND u.deleted_at IS NULL
        
        UNION ALL
        
        SELECT 'TEACHING_ASSISTANT' as role_name, COUNT(DISTINCT ct.user_id) as count
        FROM class_teachers ct
        JOIN users u ON ct.user_id = u.id
        WHERE ct.class_id = :classId 
        AND ct.role_in_class = 'TEACHING_ASSISTANT'
        AND ct.status = 'ACTIVE'
        AND ct.deleted_at IS NULL
        AND u.deleted_at IS NULL
        
        UNION ALL
        
        SELECT 'STUDENT' as role_name, COUNT(DISTINCT cs.user_id) as count
        FROM class_students cs
        JOIN users u ON cs.user_id = u.id
        WHERE cs.class_id = :classId 
        AND cs.status = 'ACTIVE'
        AND cs.deleted_at IS NULL
        AND u.deleted_at IS NULL
        """, nativeQuery = true)
    List<Map<String, Object>> getMemberCountByRole(@Param("classId") Long classId);

    @Query(value = """
        SELECT COUNT(*) 
        FROM daily_challenges dc
        JOIN class_lessons cl ON dc.class_lesson_id = cl.id
        JOIN class_chapters cc ON cl.class_chapter_id = cc.id
        WHERE cc.class_id = :classId
        AND dc.deleted_at IS NULL
        """, nativeQuery = true)
    Long countChallengesByClass(@Param("classId") Long classId);

    @Query(value = """
        SELECT 
            u.id as user_id,
            u.full_name,
            u.email,
            u.avatar_url,
            ct.role_in_class,
            COUNT(DISTINCT dc.id) as assigned_challenges,
            COUNT(DISTINCT gdc.id) as graded_submissions
        FROM users u
        JOIN class_teachers ct ON u.id = ct.user_id
        LEFT JOIN daily_challenges dc ON dc.created_by = u.email
        LEFT JOIN grading_daily_challenges gdc ON gdc.grader_id = u.id
        WHERE ct.class_id = :classId
        AND ct.status = 'ACTIVE'
        AND ct.deleted_at IS NULL
        AND u.deleted_at IS NULL
        GROUP BY u.id, u.full_name, u.email, u.avatar_url, ct.role_in_class
        """, nativeQuery = true)
    List<Map<String, Object>> getTeacherActivities(@Param("classId") Long classId);


    @Query(value = """
    WITH ranked_submissions AS (
        SELECT 
            sdc.user_id,
            gdc.raw_score * (1 - gdc.penalty_applied) as score,
            sdc.submitted_at,
            ROW_NUMBER() OVER (PARTITION BY sdc.user_id ORDER BY sdc.submitted_at DESC) as rn
        FROM submission_daily_challenges sdc
        JOIN daily_challenges dc ON sdc.challenge_id = dc.id
        JOIN class_lessons cl ON dc.class_lesson_id = cl.id
        JOIN class_chapters cc ON cl.class_chapter_id = cc.id
        JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id
        WHERE cc.class_id = :classId
        AND sdc.submission_status IN ('SUBMITTED', 'GRADED')
        AND sdc.deleted_at IS NULL
        AND gdc.deleted_at IS NULL
        AND dc.deleted_at IS NULL
    ),
    last_score AS (
        SELECT user_id, score
        FROM ranked_submissions
        WHERE rn = 1
    ),
    previous_score AS (
        SELECT user_id, score
        FROM ranked_submissions
        WHERE rn = 2
    )
    SELECT 
        u.id as user_id,
        u.full_name,
        u.email,
        u.avatar_url,
        COALESCE(AVG(gdc.raw_score * (1 - gdc.penalty_applied)), 0) as average_score,
        COUNT(DISTINCT sdc.id) as total_submissions,
        COUNT(DISTINCT CASE WHEN sdc.is_late = true THEN sdc.id END) as late_submissions,
        COUNT(DISTINCT CASE WHEN sdc.is_late = false AND sdc.submission_status IN ('SUBMITTED', 'GRADED') THEN sdc.id END) as ontime_submissions,
        COALESCE(ls.score - ps.score, 0) as improvement_score
    FROM users u
    JOIN class_students cs ON u.id = cs.user_id
    LEFT JOIN submission_daily_challenges sdc ON u.id = sdc.user_id AND sdc.deleted_at IS NULL
    LEFT JOIN daily_challenges dc ON sdc.challenge_id = dc.id
    LEFT JOIN class_lessons cl ON dc.class_lesson_id = cl.id
    LEFT JOIN class_chapters cc ON cl.class_chapter_id = cc.id AND cc.class_id = :classId
    LEFT JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id AND gdc.deleted_at IS NULL
    LEFT JOIN last_score ls ON u.id = ls.user_id
    LEFT JOIN previous_score ps ON u.id = ps.user_id
    WHERE cs.class_id = :classId
    AND cs.status = 'ACTIVE'
    AND cs.deleted_at IS NULL
    AND u.deleted_at IS NULL
    GROUP BY u.id, u.full_name, u.email, u.avatar_url, ls.score, ps.score
    """, nativeQuery = true)
    List<Map<String, Object>> getStudentRankings(@Param("classId") Long classId);

    /**
     * FIXED: Challenge stats by skill using grading_daily_challenges
     */
    @Query(value = """
        SELECT 
            dc.id as challenge_id,
            dc.challenge_name,
            dc.challenge_type,
            COUNT(DISTINCT CASE WHEN sdc.is_late = false AND sdc.submission_status IN ('SUBMITTED', 'GRADED') THEN sdc.id END) as ontime_count,
            COUNT(DISTINCT CASE WHEN sdc.is_late = true THEN sdc.id END) as late_count,
            (SELECT COUNT(*) FROM class_students WHERE class_id = :classId AND status = 'ACTIVE' AND deleted_at IS NULL) - 
            COUNT(DISTINCT CASE WHEN sdc.submission_status IN ('SUBMITTED', 'GRADED') THEN sdc.id END) as not_submitted_count,
            COALESCE(AVG(gdc.raw_score * (1 - gdc.penalty_applied)), 0) as average_score
        FROM daily_challenges dc
        JOIN class_lessons cl ON dc.class_lesson_id = cl.id
        JOIN class_chapters cc ON cl.class_chapter_id = cc.id
        LEFT JOIN submission_daily_challenges sdc ON dc.id = sdc.challenge_id AND sdc.deleted_at IS NULL
        LEFT JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id AND gdc.deleted_at IS NULL
        WHERE cc.class_id = :classId
        AND dc.challenge_type = :skillType
        AND dc.deleted_at IS NULL
        GROUP BY dc.id, dc.challenge_name, dc.challenge_type
        """, nativeQuery = true)
    List<Map<String, Object>> getChallengeStatsBySkill(@Param("classId") Long classId, @Param("skillType") String skillType);

    @Query(value = """
        SELECT 
            dc.challenge_type as skill,
            dc.status,
            COUNT(*) as count
        FROM daily_challenges dc
        JOIN class_lessons cl ON dc.class_lesson_id = cl.id
        JOIN class_chapters cc ON cl.class_chapter_id = cc.id
        WHERE cc.class_id = :classId
        AND dc.deleted_at IS NULL
        GROUP BY dc.challenge_type, dc.status
        """, nativeQuery = true)
    List<Map<String, Object>> getChallengeProgressBySkill(@Param("classId") Long classId);

    /* --------------------------------------------------------
     * CHALLENGE REPORT QUERIES
     * -------------------------------------------------------- */

    /**
     * FIXED: Challenge overview using grading_daily_challenges
     */
    @Query(value = """
        SELECT 
            COALESCE(AVG(gdc.raw_score * (1 - gdc.penalty_applied)), 0) as average_score,
            COALESCE(MAX(gdc.raw_score * (1 - gdc.penalty_applied)), 0) as highest_score,
            COALESCE(MIN(gdc.raw_score * (1 - gdc.penalty_applied)), 0) as lowest_score,
            COUNT(DISTINCT CASE WHEN sdc.submission_status IN ('SUBMITTED', 'GRADED') THEN sdc.id END) as completed_count,
            COUNT(DISTINCT CASE WHEN sdc.is_late = true THEN sdc.id END) as late_count,
            COUNT(DISTINCT CASE WHEN sdc.submission_status IN ('PENDING', 'NOT_STARTED', 'MISSED') THEN sdc.id END) as not_started_count
        FROM daily_challenges dc
        LEFT JOIN submission_daily_challenges sdc ON dc.id = sdc.challenge_id AND sdc.deleted_at IS NULL
        LEFT JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id AND gdc.deleted_at IS NULL
        WHERE dc.id = :challengeId
        AND dc.deleted_at IS NULL
        """, nativeQuery = true)
    Map<String, Object> getChallengeOverviewStats(@Param("challengeId") Long challengeId);

    /**
     * FIXED: Student performance by challenge using grading_daily_challenges
     */
    @Query(value = """
        SELECT 
            u.id as user_id,
            u.full_name,
            u.email,
            u.avatar_url,
            COALESCE(gdc.raw_score * (1 - gdc.penalty_applied), 0) as score,
            CASE 
                WHEN sdc.actual_start_at IS NOT NULL AND sdc.submitted_at IS NOT NULL 
                THEN EXTRACT(EPOCH FROM (sdc.submitted_at - sdc.actual_start_at)) / 60
                ELSE NULL 
            END as completion_time_minutes,
            sdc.submission_status,
            sdc.is_late,
            sdc.submitted_at,
            sdc.started_at
        FROM users u
        JOIN class_students cs ON u.id = cs.user_id
        JOIN class_chapters cc ON cs.class_id = cc.class_id
        JOIN class_lessons cl ON cc.id = cl.class_chapter_id
        JOIN daily_challenges dc ON cl.id = dc.class_lesson_id
        LEFT JOIN submission_daily_challenges sdc ON u.id = sdc.user_id AND dc.id = sdc.challenge_id AND sdc.deleted_at IS NULL
        LEFT JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id AND gdc.deleted_at IS NULL
        WHERE dc.id = :challengeId
        AND cs.status = 'ACTIVE'
        AND cs.deleted_at IS NULL
        AND u.deleted_at IS NULL
        AND dc.deleted_at IS NULL
        ORDER BY score DESC
        """, nativeQuery = true)
    List<Map<String, Object>> getStudentPerformanceByChallenge(@Param("challengeId") Long challengeId);

    /* --------------------------------------------------------
     * STUDENT PERFORMANCE QUERIES
     * -------------------------------------------------------- */

    @Query(value = """
        SELECT MIN(cs.joined_at)
        FROM class_students cs
        WHERE cs.user_id = :userId
          AND cs.deleted_at IS NULL
    """, nativeQuery = true)
    Instant getFirstClassJoinedDate(@Param("userId") Long userId);

    @Query(value = """
        SELECT 
            c.id as class_id,
            c.class_name,
            c.class_code,
            cs.joined_at,
            l.id as level_id,
            l.level_name,
            l.level_code,
            l.description
        FROM class_students cs
        JOIN classes c ON cs.class_id = c.id
        LEFT JOIN syllabuses s ON c.syllabus_id = s.id
        LEFT JOIN levels l ON s.level_id = l.id
        WHERE cs.user_id = :userId
        AND cs.status = 'ACTIVE'
        AND cs.deleted_at IS NULL
        AND c.deleted_at IS NULL
        ORDER BY cs.joined_at DESC
        LIMIT 1
        """, nativeQuery = true)
    Map<String, Object> getCurrentClassInfo(@Param("userId") Long userId);

    @Query(value = """
        SELECT 
            COUNT(DISTINCT CASE WHEN sdc.submission_status IN ('SUBMITTED', 'GRADED') AND sdc.is_late = false THEN sdc.id END) as completed_count,
            COUNT(DISTINCT CASE WHEN sdc.is_late = true THEN sdc.id END) as late_count,
            COUNT(DISTINCT CASE WHEN sdc.submission_status IN ('PENDING', 'NOT_STARTED', 'MISSED') THEN sdc.id END) as not_started_count,
            COUNT(DISTINCT dc.id) as total_challenges
        FROM daily_challenges dc
        JOIN class_lessons cl ON dc.class_lesson_id = cl.id
        JOIN class_chapters cc ON cl.class_chapter_id = cc.id
        LEFT JOIN submission_daily_challenges sdc ON dc.id = sdc.challenge_id AND sdc.user_id = :userId AND sdc.deleted_at IS NULL
        WHERE cc.class_id = :classId
        AND dc.deleted_at IS NULL
        """, nativeQuery = true)
    Map<String, Object> getStudentChallengeProgress(@Param("userId") Long userId, @Param("classId") Long classId);

    @Query(value = """
        SELECT 
            l.id as level_id,
            l.level_name,
            l.level_code,
            c.id as class_id,
            c.class_name,
            c.class_code,
            cs.joined_at,
            cs.left_at
        FROM class_students cs
        JOIN classes c ON cs.class_id = c.id
        LEFT JOIN syllabuses s ON c.syllabus_id = s.id
        LEFT JOIN levels l ON s.level_id = l.id
        WHERE cs.user_id = :userId
        AND cs.deleted_at IS NULL
        AND c.deleted_at IS NULL
        ORDER BY cs.joined_at DESC
        """, nativeQuery = true)
    List<Map<String, Object>> getStudentLevelHistory(@Param("userId") Long userId);

    /**
     * FIXED: Get scores by class and type using grading_daily_challenges
     */
    @Query(value = """
        SELECT 
            dc.challenge_type,
            COALESCE(AVG(gdc.raw_score * (1 - gdc.penalty_applied)), 0) as average_score
        FROM daily_challenges dc
        JOIN class_lessons cl ON dc.class_lesson_id = cl.id
        JOIN class_chapters cc ON cl.class_chapter_id = cc.id
        LEFT JOIN submission_daily_challenges sdc ON dc.id = sdc.challenge_id 
            AND sdc.user_id = :userId AND sdc.deleted_at IS NULL
        LEFT JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id AND gdc.deleted_at IS NULL
        WHERE cc.class_id = :classId
        AND cc.deleted_at IS NULL
        AND cl.deleted_at IS NULL
        AND dc.deleted_at IS NULL
        GROUP BY dc.challenge_type
        """, nativeQuery = true)
    List<Map<String, Object>> getScoresByClassAndType(@Param("userId") Long userId, @Param("classId") Long classId);

    /**
     * FIXED: Student challenges by class using grading_daily_challenges
     */
    @Query(value = """
        SELECT 
            dc.id as challenge_id,
            dc.challenge_name,
            dc.challenge_type,
            COALESCE(gdc.raw_score * (1 - gdc.penalty_applied), 0) as score,
            sdc.is_late,
            sdc.submission_status,
            sdc.submitted_at
        FROM daily_challenges dc
        JOIN class_lessons cl ON dc.class_lesson_id = cl.id
        JOIN class_chapters cc ON cl.class_chapter_id = cc.id
        LEFT JOIN submission_daily_challenges sdc ON dc.id = sdc.challenge_id AND sdc.user_id = :userId AND sdc.deleted_at IS NULL
        LEFT JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id AND gdc.deleted_at IS NULL
        WHERE cc.class_id = :classId
        AND dc.deleted_at IS NULL
        ORDER BY dc.created_at
        """, nativeQuery = true)
    List<Map<String, Object>> getStudentChallengesByClass(@Param("userId") Long userId, @Param("classId") Long classId);
}