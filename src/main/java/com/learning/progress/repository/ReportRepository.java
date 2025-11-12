package com.learning.progress.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface ReportRepository extends JpaRepository<com.learning.progress.entity.DailyChallenge, Long> {

    /* --------------------------------------------------------
     * CLASS REPORT QUERIES
     * -------------------------------------------------------- */

    /**
     * Get average score for all finished challenges in a class
     */
    @Query(value = """
        SELECT COALESCE(AVG(gq.received_weight / q.weight * 100), 0) as avgScore
        FROM grading_questions gq
        JOIN submission_questions sq ON gq.submission_question_id = sq.id
        JOIN questions q ON sq.question_id = q.id
        JOIN submission_daily_challenges sdc ON sq.submission_daily_id = sdc.id
        JOIN daily_challenges dc ON sdc.challenge_id = dc.id
        JOIN class_lessons cl ON dc.class_lesson_id = cl.id
        JOIN class_chapters cc ON cl.class_chapter_id = cc.id
        WHERE cc.class_id = :classId
        AND dc.deleted_at IS NULL
        AND sdc.deleted_at IS NULL
        AND sdc.submission_status IN ('SUBMITTED', 'GRADED')
        AND gq.deleted_at IS NULL
        AND q.deleted_at IS NULL
        """, nativeQuery = true)
    BigDecimal getAverageScoreByClass(@Param("classId") Long classId);

    /**
     * Get completion rate (finished challenges / total challenges)
     */
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

    /**
     * Count total and completed lessons in a class
     */
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

    /**
     * Count members by role
     */
    @Query(value = """
        SELECT 
            r.name as role_name,
            COUNT(DISTINCT CASE 
                WHEN r.name IN ('TEACHER', 'TEACHING_ASSISTANT') 
                THEN ct.user_id 
                ELSE cs.user_id 
            END) as count
        FROM roles r
        LEFT JOIN users u ON u.role_id = r.id AND u.deleted_at IS NULL
        LEFT JOIN class_teachers ct ON u.id = ct.user_id 
            AND ct.class_id = :classId 
            AND ct.status = 'ACTIVE'
            AND ct.deleted_at IS NULL
        LEFT JOIN class_students cs ON u.id = cs.user_id 
            AND cs.class_id = :classId 
            AND cs.status = 'ACTIVE'
            AND cs.deleted_at IS NULL
        WHERE r.name IN ('TEACHER', 'TEACHING_ASSISTANT', 'STUDENT', 'TEST_TAKER')
        GROUP BY r.name
        """, nativeQuery = true)
    List<Map<String, Object>> getMemberCountByRole(@Param("classId") Long classId);

    /**
     * Count total challenges in class
     */
    @Query(value = """
        SELECT COUNT(*) 
        FROM daily_challenges dc
        JOIN class_lessons cl ON dc.class_lesson_id = cl.id
        JOIN class_chapters cc ON cl.class_chapter_id = cc.id
        WHERE cc.class_id = :classId
        AND dc.deleted_at IS NULL
        """, nativeQuery = true)
    Long countChallengesByClass(@Param("classId") Long classId);

    /**
     * Get teacher/TA activities
     */
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

    /**
     * Get student rankings with scores and submission stats
     */
    @Query(value = """
        SELECT 
            u.id as user_id,
            u.full_name,
            u.email,
            u.avatar_url,
            COALESCE(AVG(gq.received_weight / q.weight * 100), 0) as average_score,
            COUNT(DISTINCT sdc.id) as total_submissions,
            COUNT(DISTINCT CASE WHEN sdc.is_late = true THEN sdc.id END) as late_submissions,
            COUNT(DISTINCT CASE WHEN sdc.is_late = false AND sdc.submission_status IN ('SUBMITTED', 'GRADED') THEN sdc.id END) as ontime_submissions
        FROM users u
        JOIN class_students cs ON u.id = cs.user_id
        LEFT JOIN submission_daily_challenges sdc ON u.id = sdc.user_id AND sdc.deleted_at IS NULL
        LEFT JOIN submission_questions sq ON sdc.id = sq.submission_daily_id AND sq.deleted_at IS NULL
        LEFT JOIN grading_questions gq ON sq.id = gq.submission_question_id AND gq.deleted_at IS NULL
        LEFT JOIN questions q ON sq.question_id = q.id AND q.deleted_at IS NULL
        WHERE cs.class_id = :classId
        AND cs.status = 'ACTIVE'
        AND cs.deleted_at IS NULL
        AND u.deleted_at IS NULL
        GROUP BY u.id, u.full_name, u.email, u.avatar_url
        ORDER BY 
            CASE WHEN :sortBy = 'score' THEN AVG(gq.received_weight / q.weight * 100) END DESC,
            CASE WHEN :sortBy = 'diligence' THEN COUNT(DISTINCT sdc.id) END DESC,
            CASE WHEN :sortBy = 'diligence' THEN COUNT(DISTINCT CASE WHEN sdc.is_late = true THEN sdc.id END) END ASC
        """, nativeQuery = true)
    List<Map<String, Object>> getStudentRankings(@Param("classId") Long classId, @Param("sortBy") String sortBy);

    /**
     * Get challenge statistics by skill (only FINISHED)
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
            COALESCE(AVG(gq.received_weight / q.weight * 100), 0) as average_score
        FROM daily_challenges dc
        JOIN class_lessons cl ON dc.class_lesson_id = cl.id
        JOIN class_chapters cc ON cl.class_chapter_id = cc.id
        LEFT JOIN submission_daily_challenges sdc ON dc.id = sdc.challenge_id AND sdc.deleted_at IS NULL
        LEFT JOIN submission_questions sq ON sdc.id = sq.submission_daily_id AND sq.deleted_at IS NULL
        LEFT JOIN grading_questions gq ON sq.id = gq.submission_question_id AND gq.deleted_at IS NULL
        LEFT JOIN questions q ON sq.question_id = q.id AND q.deleted_at IS NULL
        WHERE cc.class_id = :classId
        AND dc.challenge_type = :skillType
        AND dc.status = 'FINISHED'
        AND dc.deleted_at IS NULL
        GROUP BY dc.id, dc.challenge_name, dc.challenge_type
        ORDER BY dc.created_at
        """, nativeQuery = true)
    List<Map<String, Object>> getChallengeStatsBySkill(@Param("classId") Long classId, @Param("skillType") String skillType);

    /**
     * Get challenge progress by skill and status
     */
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
     * Get challenge overview stats
     */
    @Query(value = """
        SELECT 
            COALESCE(AVG(gq.received_weight / q.weight * 100), 0) as average_score,
            COALESCE(MAX(gq.received_weight / q.weight * 100), 0) as highest_score,
            COALESCE(MIN(gq.received_weight / q.weight * 100), 0) as lowest_score,
            COUNT(DISTINCT CASE WHEN sdc.submission_status IN ('SUBMITTED', 'GRADED') THEN sdc.id END) as completed_count,
            COUNT(DISTINCT CASE WHEN sdc.is_late = true THEN sdc.id END) as late_count,
            COUNT(DISTINCT CASE WHEN sdc.submission_status IN ('PENDING', 'NOT_STARTED', 'MISSED') THEN sdc.id END) as not_started_count
        FROM daily_challenges dc
        LEFT JOIN submission_daily_challenges sdc ON dc.id = sdc.challenge_id AND sdc.deleted_at IS NULL
        LEFT JOIN submission_questions sq ON sdc.id = sq.submission_daily_id AND sq.deleted_at IS NULL
        LEFT JOIN grading_questions gq ON sq.id = gq.submission_question_id AND gq.deleted_at IS NULL
        LEFT JOIN questions q ON sq.question_id = q.id AND q.deleted_at IS NULL
        WHERE dc.id = :challengeId
        AND dc.deleted_at IS NULL
        """, nativeQuery = true)
    Map<String, Object> getChallengeOverviewStats(@Param("challengeId") Long challengeId);

    /**
     * Get student performance for a challenge
     */
    @Query(value = """
        SELECT 
            u.id as user_id,
            u.full_name,
            u.email,
            u.avatar_url,
            COALESCE(AVG(gq.received_weight / q.weight * 100), 0) as score,
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
        LEFT JOIN submission_questions sq ON sdc.id = sq.submission_daily_id AND sq.deleted_at IS NULL
        LEFT JOIN grading_questions gq ON sq.id = gq.submission_question_id AND gq.deleted_at IS NULL
        LEFT JOIN questions q ON sq.question_id = q.id AND q.deleted_at IS NULL
        WHERE dc.id = :challengeId
        AND cs.status = 'ACTIVE'
        AND cs.deleted_at IS NULL
        AND u.deleted_at IS NULL
        AND dc.deleted_at IS NULL
        GROUP BY u.id, u.full_name, u.email, u.avatar_url, sdc.submission_status, sdc.is_late, 
                 sdc.submitted_at, sdc.started_at, sdc.actual_start_at
        ORDER BY score DESC
        """, nativeQuery = true)
    List<Map<String, Object>> getStudentPerformanceByChallenge(@Param("challengeId") Long challengeId);

    /* --------------------------------------------------------
     * STUDENT PERFORMANCE QUERIES
     * -------------------------------------------------------- */

    /**
     * Get student's first class joined date
     */
    @Query(value = """
        SELECT MIN(cs.joined_at)
        FROM class_students cs
        WHERE cs.user_id = :userId
          AND cs.deleted_at IS NULL
    """, nativeQuery = true)
    Instant getFirstClassJoinedDate(@Param("userId") Long userId);


    /**
     * Get student's current active class
     */
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

    /**
     * Get student's challenge progress in current class
     */
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

    /**
     * Get student's level history with class details
     */
    @Query(value = """
        SELECT 
            l.id as level_id,
            l.level_name,
            l.level_code,
            c.id as class_id,
            c.class_name,
            c.class_code,
            cs.joined_at,
            cs.left_at,
            dc.challenge_type,
            COALESCE(AVG(gdc.raw_score * (1-gdc.penalty_applied)), 0) as average_score
        FROM class_students cs
        JOIN classes c ON cs.class_id = c.id
        LEFT JOIN syllabuses s ON c.syllabus_id = s.id
        LEFT JOIN levels l ON s.level_id = l.id
        LEFT JOIN class_chapters cc ON c.id = cc.class_id AND cc.deleted_at IS NULL
        LEFT JOIN class_lessons cl ON cc.id = cl.class_chapter_id AND cl.deleted_at IS NULL
        LEFT JOIN daily_challenges dc ON cl.id = dc.class_lesson_id AND dc.deleted_at IS NULL
        LEFT JOIN submission_daily_challenges sdc ON dc.id = sdc.challenge_id AND sdc.user_id = :userId AND sdc.deleted_at IS NULL
        LEFT JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id AND gdc.deleted_at IS NULL
        LEFT JOIN submission_questions sq ON sdc.id = sq.submission_daily_id AND sq.deleted_at IS NULL
        LEFT JOIN grading_questions gq ON sq.id = gq.submission_question_id AND gq.deleted_at IS NULL
        LEFT JOIN questions q ON sq.question_id = q.id AND q.deleted_at IS NULL
        WHERE cs.user_id = :userId
        AND cs.deleted_at IS NULL
        AND c.deleted_at IS NULL
        GROUP BY l.id, l.level_name, l.level_code, c.id, c.class_name, c.class_code, cs.joined_at, cs.left_at, dc.challenge_type
        ORDER BY cs.joined_at DESC
        """, nativeQuery = true)
    List<Map<String, Object>> getStudentLevelHistory(@Param("userId") Long userId);

    /**
     * Get student's challenge scores by class
     */
    @Query(value = """
        SELECT 
            dc.id as challenge_id,
            dc.challenge_name,
            dc.challenge_type,
            COALESCE(AVG(gq.received_weight / q.weight * 100), 0) as score,
            sdc.is_late,
            sdc.submission_status,
            sdc.submitted_at
        FROM daily_challenges dc
        JOIN class_lessons cl ON dc.class_lesson_id = cl.id
        JOIN class_chapters cc ON cl.class_chapter_id = cc.id
        LEFT JOIN submission_daily_challenges sdc ON dc.id = sdc.challenge_id AND sdc.user_id = :userId AND sdc.deleted_at IS NULL
        LEFT JOIN submission_questions sq ON sdc.id = sq.submission_daily_id AND sq.deleted_at IS NULL
        LEFT JOIN grading_questions gq ON sq.id = gq.submission_question_id AND gq.deleted_at IS NULL
        LEFT JOIN questions q ON sq.question_id = q.id AND q.deleted_at IS NULL
        WHERE cc.class_id = :classId
        AND dc.deleted_at IS NULL
        GROUP BY dc.id, dc.challenge_name, dc.challenge_type, sdc.is_late, sdc.submission_status, sdc.submitted_at
        ORDER BY dc.created_at
        """, nativeQuery = true)
    List<Map<String, Object>> getStudentChallengesByClass(@Param("userId") Long userId, @Param("classId") Long classId);
}