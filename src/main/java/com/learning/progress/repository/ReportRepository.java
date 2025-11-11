package com.learning.progress.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface ReportRepository extends JpaRepository<Object, Long> {

    // ==============================================================
    // 1. CLASS REPORT – Tổng quan lớp
    // ==============================================================
    @Query(value = """
        WITH stats AS (
            SELECT 
                COUNT(DISTINCT s.id) AS total_students,
                COUNT(DISTINCT CASE WHEN cs.status = 'ACTIVE' THEN cs.user_id END) AS active_students,
                COUNT(DISTINCT s.id) FILTER (WHERE s.submission_status IN ('SUBMITTED','GRADED')) AS submitted_count,
                COUNT(DISTINCT s.id) FILTER (WHERE s.submission_status = 'MISSED') AS missed_count,
                COUNT(DISTINCT s.id) FILTER (WHERE s.is_late = true) AS late_count,
                AVG(g.total_score) AS avg_score
            FROM submission_daily_challenge s
            JOIN daily_challenge dc ON dc.id = s.challenge_id AND dc.deleted_at IS NULL
            JOIN class_lesson cl ON cl.id = dc.class_lesson_id
            JOIN class_chapter cch ON cch.id = cl.class_chapter_id
            JOIN clazz c ON c.id = cch.clazz_id AND c.id = :classId
            LEFT JOIN grading_daily_challenge g ON g.submission_daily_id = s.id AND g.deleted_at IS NULL
            LEFT JOIN class_student cs ON cs.user_id = s.user_id AND cs.class_id = :classId AND cs.deleted_at IS NULL
            WHERE s.deleted_at IS NULL
              AND (:from IS NULL OR dc.end_date >= :from)
              AND (:to IS NULL OR dc.start_date <= :to)
        )
        SELECT 
            total_students,
            active_students,
            COALESCE(ROUND(submitted_count::numeric / NULLIF(total_students,0) * 100, 2), 0) AS submission_rate,
            COALESCE(ROUND((submitted_count - late_count)::numeric / NULLIF(total_students,0) * 100, 2), 0) AS on_time_rate,
            ROUND(COALESCE(avg_score, 0), 2) AS avg_score,
            missed_count
        FROM stats
        """, nativeQuery = true)
    Map<String, Object> getClassSummaryStats(
            @Param("classId") Long classId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
// Ví dụ vài query quan trọng (bạn chỉ cần thêm vào file cũ)

    @Query(value = """
    SELECT 
        COUNT(*) AS total,
        COUNT(CASE WHEN cl.status = 'COMPLETED' THEN 1 END) AS completed
    FROM class_lesson cl
    JOIN daily_challenge dc ON dc.class_lesson_id = cl.id
    JOIN class_chapter cch ON cch.id = cl.class_chapter_id
    WHERE cch.clazz_id = :classId
    """, nativeQuery = true)
    Map<String, Object> getLessonProgress(@Param("classId") Long classId);

    @Query(value = """
    SELECT 
        u.full_name AS teacher_name,
        COUNT(g.id) AS graded_count
    FROM grading_daily_challenge g
    JOIN users u ON u.id = g.graded_by
    JOIN submission_daily_challenge s ON s.id = g.submission_daily_id
    JOIN daily_challenge dc ON dc.id = s.challenge_id
    JOIN class_lesson cl ON cl.id = dc.class_lesson_id
    JOIN class_chapter cch ON cch.id = cl.class_chapter_id
    WHERE cch.clazz_id = :classId AND g.deleted_at IS NULL
    GROUP BY u.id, u.full_name
    ORDER BY graded_count DESC
    """, nativeQuery = true)
    List<Map<String, Object>> getTeacherGradingStats(@Param("classId") Long classId);
    // Top 5 challenge gần nhất của lớp
    @Query(value = """
        SELECT 
            dc.id AS challenge_id,
            dc.title,
            dc.end_date,
            COUNT(s.id) FILTER (WHERE s.submission_status IN ('SUBMITTED','GRADED')) AS submitted,
            COUNT(s.id) AS total,
            ROUND(COUNT(s.id) FILTER (WHERE s.submission_status IN ('SUBMITTED','GRADED'))::numeric / NULLIF(COUNT(s.id),0) * 100, 2) AS rate
        FROM daily_challenge dc
        JOIN class_lesson cl ON cl.id = dc.class_lesson_id
        JOIN class_chapter cch ON cch.id = cl.class_chapter_id
        JOIN clazz c ON c.id = cch.clazz_id AND c.id = :classId
        LEFT JOIN submission_daily_challenge s ON s.challenge_id = dc.id AND s.deleted_at IS NULL
        WHERE dc.deleted_at IS NULL
          AND dc.challenge_status != 'DRAFT'
        GROUP BY dc.id, dc.title, dc.end_date
        ORDER BY dc.end_date DESC
        LIMIT 5
        """, nativeQuery = true)
    List<Map<String, Object>> getRecentChallenges(@Param("classId") Long classId);

    // Học sinh yếu (điểm < 5 hoặc missed >= 3)
    @Query(value = """
        SELECT 
            u.id AS student_id,
            u.full_name AS name,
            ROUND(AVG(g.total_score), 2) AS avg_score,
            COUNT(CASE WHEN s.submission_status = 'MISSED' THEN 1 END) AS missed_count
        FROM submission_daily_challenge s
        JOIN daily_challenge dc ON dc.id = s.challenge_id
        JOIN class_lesson cl ON cl.id = dc.class_lesson_id
        JOIN class_chapter cch ON cch.id = cl.class_chapter_id
        JOIN clazz c ON c.id = cch.clazz_id AND c.id = :classId
        JOIN users u ON u.id = s.user_id
        LEFT JOIN grading_daily_challenge g ON g.submission_daily_id = s.id AND g.deleted_at IS NULL
        WHERE s.deleted_at IS NULL
          AND (:from IS NULL OR dc.end_date >= :from)
          AND (:to IS NULL OR dc.start_date <= :to)
        GROUP BY u.id, u.full_name
        HAVING AVG(g.total_score) < 5 OR COUNT(CASE WHEN s.submission_status = 'MISSED' THEN 1 END) >= 3
        ORDER BY avg_score ASC NULLS LAST
        LIMIT 10
        """, nativeQuery = true)
    List<Map<String, Object>> getWeakStudents(
            @Param("classId") Long classId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    // ==============================================================
    // 2. DAILY CHALLENGE REPORT – Chi tiết 1 bài
    // ==============================================================
    @Query(value = """
        SELECT 
            s.id AS submission_id,
            u.id AS student_id,
            u.full_name AS student_name,
            s.submission_status,
            g.total_score AS score,
            s.is_late,
            s.expired_at > dc.end_date AS extended,
            EXISTS(SELECT 1 FROM submission_daily_challenge s2 
                   WHERE s2.user_id = s.user_id 
                     AND s2.challenge_id = s.challenge_id 
                     AND s2.deleted_at IS NOT NULL 
                     AND s2.id != s.id) AS reset
        FROM submission_daily_challenge s
        JOIN users u ON u.id = s.user_id
        JOIN daily_challenge dc ON dc.id = s.challenge_id AND dc.id = :challengeId
        LEFT JOIN grading_daily_challenge g ON g.submission_daily_id = s.id AND g.deleted_at IS NULL
        WHERE s.deleted_at IS NULL
          AND (:search IS NULL OR u.full_name ILIKE '%' || :search || '%')
        ORDER BY u.full_name
        LIMIT :size OFFSET :offset
        """, nativeQuery = true)
    List<Map<String, Object>> getChallengeStudentDetails(
            @Param("challengeId") Long challengeId,
            @Param("search") String search,
            @Param("size") int size,
            @Param("offset") int offset);

    @Query(value = "SELECT COUNT(*) FROM submission_daily_challenge s WHERE s.challenge_id = :challengeId AND s.deleted_at IS NULL", nativeQuery = true)
    long countChallengeSubmissions(@Param("challengeId") Long challengeId);

    // ==============================================================
    // 3. STUDENT PERFORMANCE – Xuyên lớp
    // ==============================================================
    @Query(value = """
        SELECT 
            COUNT(DISTINCT s.challenge_id) AS total_challenges,
            COUNT(DISTINCT CASE WHEN s.submission_status IN ('SUBMITTED','GRADED') THEN s.challenge_id END) AS completed,
            ROUND(AVG(g.total_score), 2) AS avg_score,
            ROUND(COUNT(CASE WHEN s.submission_status IN ('SUBMITTED','GRADED') THEN 1 END)::numeric 
                  / NULLIF(COUNT(s.id),0) * 100, 2) AS submission_rate
        FROM submission_daily_challenge s
        JOIN daily_challenge dc ON dc.id = s.challenge_id AND dc.deleted_at IS NULL
        LEFT JOIN grading_daily_challenge g ON g.submission_daily_id = s.id AND g.deleted_at IS NULL
        WHERE s.user_id = :studentId AND s.deleted_at IS NULL
          AND (:from IS NULL OR dc.end_date >= :from)
          AND (:to IS NULL OR dc.start_date <= :to)
        """, nativeQuery = true)
    Map<String, Object> getStudentOverallStats(
            @Param("studentId") Long studentId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    // Per class
    @Query(value = """
        SELECT 
            c.id AS class_id,
            c.name AS class_name,
            COUNT(DISTINCT s.challenge_id) AS challenges,
            ROUND(AVG(g.total_score), 2) AS avg_score,
            ROUND(COUNT(CASE WHEN s.submission_status IN ('SUBMITTED','GRADED') THEN 1 END)::numeric 
                  / NULLIF(COUNT(s.id),0) * 100, 2) AS submission_rate
        FROM submission_daily_challenge s
        JOIN daily_challenge dc ON dc.id = s.challenge_id
        JOIN class_lesson cl ON cl.id = dc.class_lesson_id
        JOIN class_chapter cch ON cch.id = cl.class_chapter_id
        JOIN clazz c ON c.id = cch.clazz_id
        LEFT JOIN grading_daily_challenge g ON g.submission_daily_id = s.id
        WHERE s.user_id = :studentId AND s.deleted_at IS NULL
        GROUP BY c.id, c.name
        ORDER BY avg_score DESC NULLS LAST
        """, nativeQuery = true)
    List<Map<String, Object>> getStudentPerformanceByClass(@Param("studentId") Long studentId);

    // Monthly trend
    @Query(value = """
        SELECT 
            TO_CHAR(DATE_TRUNC('month', dc.end_date), 'YYYY-MM') AS month,
            ROUND(AVG(g.total_score), 2) AS avg_score,
            ROUND(COUNT(CASE WHEN s.submission_status IN ('SUBMITTED','GRADED') THEN 1 END)::numeric 
                  / NULLIF(COUNT(s.id),0) * 100, 2) AS submission_rate
        FROM submission_daily_challenge s
        JOIN daily_challenge dc ON dc.id = s.challenge_id
        LEFT JOIN grading_daily_challenge g ON g.submission_daily_id = s.id
        WHERE s.user_id = :studentId AND s.deleted_at IS NULL
          AND (:from IS NULL OR dc.end_date >= :from)
          AND (:to IS NULL OR dc.start_date <= :to)
        GROUP BY DATE_TRUNC('month', dc.end_date)
        ORDER BY month DESC
        LIMIT 12
        """, nativeQuery = true)
    List<Map<String, Object>> getStudentMonthlyTrend(
            @Param("studentId") Long studentId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    // Thêm 2 query này nếu chưa có
    @Query(value = """
    SELECT 
        dc.title,
        dc.start_date,
        dc.end_date,
        c.name || ' - ' || COALESCE(c.subject, '') AS class_name
    FROM daily_challenge dc
    JOIN class_lesson cl ON cl.id = dc.class_lesson_id
    JOIN class_chapter cch ON cch.id = cl.class_chapter_id
    JOIN clazz c ON c.id = cch.clazz_id
    WHERE dc.id = :challengeId AND dc.deleted_at IS NULL
    """, nativeQuery = true)
    Map<String, Object> getChallengeSummary(@Param("challengeId") Long challengeId);

}