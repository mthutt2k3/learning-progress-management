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
        LEFT JOIN daily_challenges dc ON dc.created_by = split_part(u.email, '@', 1)
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
            COUNT(DISTINCT CASE WHEN sdc.submission_status IN ('DRAFT') THEN sdc.id END) as in_progress_count,
            COUNT(DISTINCT CASE WHEN sdc.submission_status IN ('PENDING', 'MISSED') THEN sdc.id END) as not_started_count
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
        CASE 
            WHEN sdc.actual_start_at IS NOT NULL AND sdc.submitted_at IS NOT NULL 
            THEN EXTRACT(EPOCH FROM (sdc.submitted_at - sdc.actual_start_at))
            ELSE NULL 
        END as completion_time_seconds,
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
            COUNT(DISTINCT CASE WHEN sdc.submission_status IN ('PENDING', 'MISSED', 'DRAFT') THEN sdc.id END) as not_started_count,
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

    @Query(value = """
    SELECT 
        dc.id as challenge_id,
        dc.challenge_name,
        sdc.submitted_at,
        CASE 
            WHEN sdc.is_late = true THEN 1 
            ELSE 0 
        END as is_late
    FROM daily_challenges dc
    JOIN class_lessons cl ON dc.class_lesson_id = cl.id
    JOIN class_chapters cc ON cl.class_chapter_id = cc.id
    JOIN classes c ON cc.class_id = c.id
    JOIN syllabuses s ON c.syllabus_id = s.id
    LEFT JOIN submission_daily_challenges sdc ON dc.id = sdc.challenge_id 
        AND sdc.user_id = :userId
        AND sdc.deleted_at IS NULL
    WHERE s.level_id = :levelId
        AND sdc.submission_status IN ('SUBMITTED', 'GRADED')
        AND dc.deleted_at IS NULL
        AND cl.deleted_at IS NULL
        AND cc.deleted_at IS NULL
        AND c.deleted_at IS NULL
    ORDER BY sdc.submitted_at DESC
    LIMIT 10
    """, nativeQuery = true)
    List<Map<String, Object>> getRecentChallengesByLevel(
            @Param("userId") Long userId,
            @Param("levelId") Long levelId
    );

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
    LEFT JOIN submission_daily_challenges sdc ON dc.id = sdc.challenge_id 
        AND sdc.deleted_at IS NULL
    LEFT JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id 
        AND gdc.deleted_at IS NULL
    WHERE cc.class_id = :classId
    AND dc.deleted_at IS NULL
    AND (sdc.user_id = :userId)
    ORDER BY dc.created_at
    """, nativeQuery = true)
    List<Map<String, Object>> getStudentChallengesByClass(@Param("userId") Long userId, @Param("classId") Long classId);

    // File: ReportRepository.java - SỬA getQuestionStats

    @Query(value = """
SELECT 
    cs.id as section_id,
    cs.section_title,
    cs.order_number as section_order,
    q.id as question_id,
    q.question_text,
    q.question_type,
    q.order_number as question_order,
    q.weight as total_weight,
    COUNT(DISTINCT sq.id) as total_attempts,
    SUM(CASE WHEN gq.received_weight >= q.weight THEN 1 ELSE 0 END) as correct_count
FROM questions q
JOIN challenge_sections cs ON q.section_id = cs.id
LEFT JOIN submission_questions sq ON q.id = sq.question_id 
    AND sq.deleted_at IS NULL
LEFT JOIN grading_questions gq ON sq.id = gq.submission_question_id 
    AND gq.deleted_at IS NULL
WHERE cs.challenge_id = :challengeId
    AND cs.deleted_at IS NULL
    AND q.deleted_at IS NULL
GROUP BY cs.id, cs.section_title, cs.order_number, q.id, q.question_text, q.question_type, q.order_number
ORDER BY cs.order_number, q.order_number
""", nativeQuery = true)
    List<Map<String, Object>> getQuestionStats(@Param("challengeId") Long challengeId);

    // File: ReportRepository.java - SỬA LẠI

    // Lấy N bài gần nhất của từng student trong class
    @Query(value = """
WITH ranked_submissions AS (
    SELECT 
        sdc.user_id,
        sdc.id as submission_id,
        gdc.raw_score * (1 - gdc.penalty_applied) as final_score,  -- FIX
        sdc.is_late,
        sdc.submission_logs_json,
        sdc.submitted_at,
        dc.challenge_type,
        ROW_NUMBER() OVER (PARTITION BY sdc.user_id ORDER BY sdc.submitted_at DESC) as rn
    FROM submission_daily_challenges sdc
    JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id
    JOIN daily_challenges dc ON sdc.challenge_id = dc.id
    JOIN class_lessons cl ON dc.class_lesson_id = cl.id
    JOIN class_chapters cc ON cl.class_chapter_id = cc.id
    WHERE cc.class_id = :classId
        AND sdc.deleted_at IS NULL
        AND gdc.deleted_at IS NULL
        AND sdc.submission_status IN ('GRADED')
        AND cc.deleted_at IS NULL
        AND cl.deleted_at IS NULL
        AND dc.deleted_at IS NULL
)
SELECT 
    u.id as user_id,
    u.full_name,
    u.email,
    u.avatar_url,
    COUNT(DISTINCT rs.submission_id) as total_submissions,
    AVG(rs.final_score) as avg_score,
    SUM(CASE WHEN rs.is_late = true THEN 1 ELSE 0 END) as late_count
FROM users u
JOIN class_students cs ON u.id = cs.user_id
LEFT JOIN ranked_submissions rs ON u.id = rs.user_id AND rs.rn <= :recentCount
WHERE cs.class_id = :classId
    AND cs.deleted_at IS NULL
    AND cs.status = 'ACTIVE'
GROUP BY u.id, u.full_name, u.email, u.avatar_url
HAVING COUNT(DISTINCT rs.submission_id) >= :minChallenges
""", nativeQuery = true)
    List<Map<String, Object>> getStudentsRecentStats(
            @Param("classId") Long classId,
            @Param("recentCount") Integer recentCount,
            @Param("minChallenges") Integer minChallenges
    );

    // Lấy N điểm gần nhất của student
    @Query(value = """
SELECT 
    gdc.raw_score * (1 - gdc.penalty_applied) as final_score,  -- FIX
    sdc.submitted_at
FROM submission_daily_challenges sdc
JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id
JOIN daily_challenges dc ON sdc.challenge_id = dc.id
JOIN class_lessons cl ON dc.class_lesson_id = cl.id
JOIN class_chapters cc ON cl.class_chapter_id = cc.id
WHERE sdc.user_id = :userId
    AND cc.class_id = :classId
    AND sdc.deleted_at IS NULL
    AND gdc.deleted_at IS NULL
    AND sdc.submission_status IN ('GRADED')
ORDER BY sdc.submitted_at DESC
LIMIT :recentCount
""", nativeQuery = true)
    List<Map<String, Object>> getRecentScores(
            @Param("userId") Long userId,
            @Param("classId") Long classId,
            @Param("recentCount") Integer recentCount
    );

    // Lấy N điểm gần nhất theo từng skill
    @Query(value = """
WITH ranked_scores AS (
    SELECT 
        dc.challenge_type,
        gdc.raw_score * (1 - gdc.penalty_applied) as final_score,  -- FIX
        sdc.submitted_at,
        ROW_NUMBER() OVER (PARTITION BY dc.challenge_type ORDER BY sdc.submitted_at DESC) as rn
    FROM submission_daily_challenges sdc
    JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id
    JOIN daily_challenges dc ON sdc.challenge_id = dc.id
    JOIN class_lessons cl ON dc.class_lesson_id = cl.id
    JOIN class_chapters cc ON cl.class_chapter_id = cc.id
    WHERE sdc.user_id = :userId
        AND cc.class_id = :classId
        AND sdc.deleted_at IS NULL
        AND gdc.deleted_at IS NULL
        AND sdc.submission_status IN ('GRADED')
)
SELECT 
    challenge_type,
    final_score,
    submitted_at
FROM ranked_scores
WHERE rn <= :recentCount
ORDER BY challenge_type, submitted_at DESC
""", nativeQuery = true)
    List<Map<String, Object>> getRecentScoresBySkill(
            @Param("userId") Long userId,
            @Param("classId") Long classId,
            @Param("recentCount") Integer recentCount
    );

    // Lấy điểm TB và điểm mới nhất của từng skill trong class
    @Query(value = """
WITH skill_scores AS (
    SELECT 
        dc.challenge_type,
        gdc.raw_score * (1 - gdc.penalty_applied) as final_score,
        sdc.submitted_at,
        ROW_NUMBER() OVER (PARTITION BY dc.challenge_type ORDER BY sdc.submitted_at DESC) as rn
    FROM submission_daily_challenges sdc
    JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id
    JOIN daily_challenges dc ON sdc.challenge_id = dc.id
    JOIN class_lessons cl ON dc.class_lesson_id = cl.id
    JOIN class_chapters cc ON cl.class_chapter_id = cc.id
    WHERE sdc.user_id = :userId
        AND cc.class_id = :classId
        AND sdc.deleted_at IS NULL
        AND gdc.deleted_at IS NULL
        AND sdc.submission_status IN ('GRADED')
        AND cc.deleted_at IS NULL
        AND cl.deleted_at IS NULL
        AND dc.deleted_at IS NULL
)
SELECT 
    challenge_type,
    AVG(final_score) as average_score,
    MAX(CASE WHEN rn = 1 THEN final_score END) as latest_score
FROM skill_scores
GROUP BY challenge_type
""", nativeQuery = true)
    List<Map<String, Object>> getSkillAverageAndLatestScore(
            @Param("userId") Long userId,
            @Param("classId") Long classId
    );

    // Lấy logs của N bài gần nhất
    @Query(value = """
    SELECT submission_logs_json
    FROM submission_daily_challenges sdc
    JOIN daily_challenges dc ON sdc.challenge_id = dc.id
    JOIN class_lessons cl ON dc.class_lesson_id = cl.id
    JOIN class_chapters cc ON cl.class_chapter_id = cc.id
    WHERE sdc.user_id = :userId
        AND cc.class_id = :classId
        AND sdc.deleted_at IS NULL
        AND sdc.submission_status IN ('GRADED')
    ORDER BY sdc.submitted_at DESC
    LIMIT :recentCount
    """, nativeQuery = true)
    List<String> getSubmissionLogs(
            @Param("userId") Long userId,
            @Param("classId") Long classId,
            @Param("recentCount") Integer recentCount
    );

    // Lấy ranking của student trong class - FIX
    @Query(value = """
WITH student_rankings AS (
    SELECT 
        sdc.user_id,
        AVG(gdc.raw_score * (1 - gdc.penalty_applied)) as avg_score,
        RANK() OVER (ORDER BY AVG(gdc.raw_score * (1 - gdc.penalty_applied)) DESC) as rank
    FROM submission_daily_challenges sdc
    JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id
    JOIN daily_challenges dc ON sdc.challenge_id = dc.id
    JOIN class_lessons cl ON dc.class_lesson_id = cl.id
    JOIN class_chapters cc ON cl.class_chapter_id = cc.id
    WHERE cc.class_id = :classId
        AND sdc.deleted_at IS NULL
        AND gdc.deleted_at IS NULL
        AND sdc.submission_status IN ('SUBMITTED', 'GRADED')
        AND cc.deleted_at IS NULL
        AND cl.deleted_at IS NULL
        AND dc.deleted_at IS NULL
    GROUP BY sdc.user_id
)
SELECT rank as ranking
FROM student_rankings
WHERE user_id = :userId
""", nativeQuery = true)
    Integer getStudentRankingInClass(
            @Param("userId") Long userId,
            @Param("classId") Long classId
    );

    // Lấy điểm TB của cả lớp - FIX
    @Query(value = """
SELECT AVG(gdc.raw_score * (1 - gdc.penalty_applied)) as class_avg_score
FROM submission_daily_challenges sdc
JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id
JOIN daily_challenges dc ON sdc.challenge_id = dc.id
JOIN class_lessons cl ON dc.class_lesson_id = cl.id
JOIN class_chapters cc ON cl.class_chapter_id = cc.id
WHERE cc.class_id = :classId
    AND sdc.deleted_at IS NULL
    AND gdc.deleted_at IS NULL
    AND sdc.submission_status IN ('SUBMITTED', 'GRADED')
    AND cc.deleted_at IS NULL
    AND cl.deleted_at IS NULL
    AND dc.deleted_at IS NULL
""", nativeQuery = true)
    BigDecimal getClassAverageScore(@Param("classId") Long classId);

    // Lấy stats của student trong class - FIX: BỎ filter FINISHED, lấy TOÀN BỘ DC
    @Query(value = """
SELECT 
    COUNT(DISTINCT CASE 
        WHEN sdc.submission_status IN ('PENDING', 'DRAFT', 'MISSED', 'SUBMITTED', 'GRADED') 
        THEN sdc.id 
    END) as total_challenges,
    COUNT(DISTINCT CASE 
        WHEN sdc.submission_status IN ('SUBMITTED', 'GRADED') 
        THEN sdc.id 
    END) as completed_challenges,
    COUNT(DISTINCT CASE 
        WHEN sdc.is_late = true 
        THEN sdc.id 
    END) as late_challenges,
    COUNT(DISTINCT CASE 
        WHEN sdc.submission_status IN ('PENDING', 'DRAFT', 'MISSED') OR sdc.id IS NULL
        THEN dc.id 
    END) as not_started_challenges,
    AVG(CASE 
        WHEN sdc.submission_status = 'GRADED'
        THEN gdc.raw_score * (1 - gdc.penalty_applied)
    END) as student_avg_score
FROM daily_challenges dc
JOIN class_lessons cl ON dc.class_lesson_id = cl.id
JOIN class_chapters cc ON cl.class_chapter_id = cc.id
LEFT JOIN submission_daily_challenges sdc ON dc.id = sdc.challenge_id 
    AND sdc.user_id = :userId
    AND sdc.deleted_at IS NULL
LEFT JOIN grading_daily_challenges gdc ON sdc.id = gdc.submission_daily_id
    AND gdc.deleted_at IS NULL
WHERE cc.class_id = :classId
    AND dc.deleted_at IS NULL
    AND cl.deleted_at IS NULL
    AND cc.deleted_at IS NULL
""", nativeQuery = true)
    Map<String, Object> getStudentChallengeStats(
            @Param("userId") Long userId,
            @Param("classId") Long classId
    );

    // Lấy class dates - giữ nguyên (không liên quan điểm)
    @Query(value = """
SELECT 
    c.start_date,
    c.end_date
FROM classes c
WHERE c.id = :classId
    AND c.deleted_at IS NULL
""", nativeQuery = true)
    Map<String, Object> getClassDates(@Param("classId") Long classId);

    // Query lấy student performance cho từng question
    @Query(value = """
SELECT 
    u.id as user_id,
    u.full_name,
    u.email,
    u.avatar_url,
    q.id as question_id,
    q.weight as total_weight,
    COALESCE(gq.received_weight, 0) as received_weight,
    sdc.submission_status
FROM questions q
JOIN challenge_sections cs ON q.section_id = cs.id
CROSS JOIN users u
JOIN class_students cls ON u.id = cls.user_id
JOIN class_chapters cc ON cls.class_id = cc.class_id
JOIN class_lessons cl ON cc.id = cl.class_chapter_id
JOIN daily_challenges dc ON cl.id = dc.class_lesson_id
LEFT JOIN submission_daily_challenges sdc ON dc.id = sdc.challenge_id 
    AND u.id = sdc.user_id
    AND sdc.deleted_at IS NULL
LEFT JOIN submission_questions sq ON q.id = sq.question_id 
    AND sdc.id = sq.submission_daily_id
    AND sq.deleted_at IS NULL
LEFT JOIN grading_questions gq ON sq.id = gq.submission_question_id
    AND gq.deleted_at IS NULL
WHERE dc.id = :challengeId
    AND cs.deleted_at IS NULL
    AND q.deleted_at IS NULL
    AND cls.status = 'ACTIVE'
    AND cls.deleted_at IS NULL
    AND u.deleted_at IS NULL
ORDER BY q.id, u.full_name
""", nativeQuery = true)
    List<Map<String, Object>> getStudentQuestionPerformances(@Param("challengeId") Long challengeId);
}