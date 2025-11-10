// src/main/java/com/learning/progress/repository/DashboardRepository.java
package com.learning.progress.repository;

import com.learning.progress.common.*;
import com.learning.progress.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.*;
import java.util.List;

@Repository
public interface DashboardRepository extends JpaRepository<User, Long> {

    // =================================================================
    // 1. Admin Dashboard - Account Growth by Role
    // =================================================================

    // COUNT
    long countByStatus(UserStatus status);
    long countByRole_Name(RoleName role);
    long countByCreatedAtAfter(OffsetDateTime date);

    // RECENT
    List<User> findTop5ByOrderByCreatedAtDesc();

    // TREND: ROLE (daily)
    @Query(value = """
    SELECT DATE(u.created_at) as d, r.name, COUNT(*) 
    FROM users u 
    JOIN roles r ON u.role_id = r.id
    WHERE u.created_at >= :start 
      AND u.deleted_at IS NULL 
    GROUP BY DATE(u.created_at), r.name 
    ORDER BY DATE(u.created_at), r.name
    """, nativeQuery = true)
    List<Object[]> findRoleByDay(@Param("start") OffsetDateTime start);

    // TREND: ROLE (monthly) - returns rows (YYYY-MM, roleName, count)
    @Query(value = """
    SELECT TO_CHAR(u.created_at, 'YYYY-MM') as m, r.name, COUNT(*) 
    FROM users u 
    JOIN roles r ON u.role_id = r.id
    WHERE u.created_at >= :start 
      AND u.deleted_at IS NULL 
    GROUP BY TO_CHAR(u.created_at, 'YYYY-MM'), r.name 
    ORDER BY TO_CHAR(u.created_at, 'YYYY-MM'), r.name
    """, nativeQuery = true)
    List<Object[]> findRoleByMonth(@Param("start") OffsetDateTime start);

    // TREND: ROLE (yearly) - returns rows (YYYY, roleName, count)
    @Query(value = """
    SELECT TO_CHAR(u.created_at, 'YYYY') as y, r.name, COUNT(*) 
    FROM users u 
    JOIN roles r ON u.role_id = r.id
    WHERE u.created_at >= :start 
      AND u.deleted_at IS NULL 
    GROUP BY TO_CHAR(u.created_at, 'YYYY'), r.name 
    ORDER BY TO_CHAR(u.created_at, 'YYYY'), r.name
    """, nativeQuery = true)
    List<Object[]> findRoleByYear(@Param("start") OffsetDateTime start);


    // =================================================================
    // 2. Admin Dashboard - Tổng quan tài khoản
    // =================================================================
    long count();

    // =================================================================
    // 3. Manager Dashboard - Summary
    // =================================================================

    @Query("SELECT COUNT(c) FROM Clazz c WHERE c.status = :status AND c.deletedAt IS NULL")
    long countByStatusAndDeletedAtIsNull(@Param("status") ClassStatus status);

    // =================================================================
    // 4. Manager Dashboard - Growth Trend (30 ngày)
    // =================================================================
    // THAY TOÀN BỘ METHOD NÀY (chỉ 1 thay đổi nhỏ!)
    @Query(value = """
    SELECT 
        DATE(u.created_at) AS register_date,
        COUNT(u.id) AS new_students,
        COALESCE(SUM(s.daily_submissions), 0) AS total_submissions
    FROM users u
    LEFT JOIN (
        SELECT 
            DATE(submitted_at) AS submission_date, 
            COUNT(*) AS daily_submissions
        FROM submission_daily_challenges
        WHERE submitted_at >= :from 
          AND submitted_at < :to
          AND deleted_at IS NULL
        GROUP BY DATE(submitted_at)
    ) s ON DATE(u.created_at) = s.submission_date
    WHERE u.role_id IN (SELECT id FROM roles WHERE name = 'STUDENT')
      AND u.created_at >= :from 
      AND u.created_at < :to
      AND u.deleted_at IS NULL
    GROUP BY DATE(u.created_at)
    ORDER BY register_date
    """, nativeQuery = true)
    List<Object[]> findDailyNewStudents(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    // =================================================================
    // 5. Manager Dashboard - At Risk Students (>7 ngày không nộp bài)
    // =================================================================
    @Query(value = """
        SELECT COUNT(DISTINCT u.id)
        FROM users u
        WHERE u.role_id IN (SELECT id FROM roles WHERE name = 'STUDENT')
          AND u.deleted_at IS NULL
          AND NOT EXISTS (
            SELECT 1 FROM submission_daily_challenges s
            WHERE s.user_id = u.id
              AND s.submitted_at >= :sevenDaysAgo
              AND s.deleted_at IS NULL
          )
        """, nativeQuery = true)
    long countAtRiskStudents(@Param("sevenDaysAgo") OffsetDateTime sevenDaysAgo);

    // =================================================================
    // 6. Manager Dashboard - Overloaded Teachers (>5 lớp)
    // =================================================================
    @Query(value = """
        SELECT COUNT(*) 
        FROM (
            SELECT user_id 
            FROM class_teachers ct
            JOIN classes c ON ct.class_id = c.id
            WHERE ct.role_in_class IN ('TEACHER', 'TEACHING_ASSISTANT')
              AND ct.status = 'ACTIVE'
              AND ct.deleted_at IS NULL
              AND c.status = 'ACTIVE'
              AND c.deleted_at IS NULL
            GROUP BY user_id
            HAVING COUNT(*) > :maxClasses
        ) AS overloaded
        """, nativeQuery = true)
    long countTeachersWithMoreThanClasses(@Param("maxClasses") int maxClasses);

    // =================================================================
    // 7. Manager Dashboard - Teacher Workload List
    // =================================================================
    @Query(value = """
        SELECT 
            u.full_name,
            COUNT(ct.class_id) AS class_count
        FROM class_teachers ct
        JOIN users u ON ct.user_id = u.id
        JOIN classes c ON ct.class_id = c.id
        WHERE ct.role_in_class IN ('TEACHER', 'TEACHING_ASSISTANT')
          AND ct.status = 'ACTIVE'
          AND ct.deleted_at IS NULL
          AND c.status = 'ACTIVE'
          AND c.deleted_at IS NULL
          AND u.deleted_at IS NULL
        GROUP BY u.id, u.full_name
        ORDER BY class_count DESC
        """, nativeQuery = true)
    List<Object[]> findTeacherClassCount();

    // =================================================================
    // 8. Manager Dashboard - Top 5 Syllabus
    // =================================================================
    @Query(value = """
    SELECT 
        s.syllabus_name AS syllabusName,
        s.syllabus_code AS syllabusCode,
        COUNT(c.id) AS classCount
    FROM syllabuses s
    LEFT JOIN classes c ON c.syllabus_id = s.id 
        AND c.status = 'ACTIVE' 
        AND c.deleted_at IS NULL
    WHERE s.deleted_at IS NULL
    GROUP BY s.id, s.syllabus_name, s.syllabus_code
    ORDER BY classCount DESC
    LIMIT 5
    """, nativeQuery = true)
    List<TopSyllabusProjection> findTop5ByClassCount();
    interface TopSyllabusProjection {
        String getSyllabusName();
        String getSyllabusCode();
        Long getClassCount();
    }
    // =================================================================
    // 9. Manager Dashboard - Low Stock Syllabus (chỉ còn 1 lớp)
    // =================================================================
    // THAY ĐỔI THÀNH CÓ THAM SỐ
    @Query(value = """
    SELECT COUNT(*)
    FROM (
        SELECT s.id
        FROM syllabuses s
        LEFT JOIN classes c ON c.syllabus_id = s.id 
            AND c.status = 'ACTIVE' AND c.deleted_at IS NULL
        WHERE s.deleted_at IS NULL
        GROUP BY s.id
        HAVING COUNT(c.id) < :threshold
    ) AS low_stock
    """, nativeQuery = true)
    long countByActiveClassCountLessThan(@Param("threshold") int threshold);

    // =================================================================
    // 10. Manager Dashboard - Level Funnel
    // =================================================================
    @Query("SELECT l FROM Level l WHERE l.status = :status ORDER BY l.orderNumber ASC")
    List<Level> findByStatusOrderByOrderNumberAsc(@Param("status") LevelEnum status);

    @Query("SELECT COUNT(sl) FROM StudentLevel sl WHERE sl.level.id = :levelId AND sl.status = :status AND sl.deletedAt IS NULL")
    long countByLevelIdAndStatus(@Param("levelId") Long levelId, @Param("status") String status);

    // =================================================================
    // 11. Completion Rate (proxy)
    // =================================================================
    @Query(value = "SELECT COUNT(*) FROM submission_daily_challenges WHERE submitted_at >= :from AND submitted_at < :to AND deleted_at IS NULL", nativeQuery = true)
    Long countBySubmittedAtBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    // Expected submissions: số học sinh * số ngày * số bài/ngày (giả sử 1 bài/ngày)
    // THAY ĐỔI METHOD NÀY (tính days tự động)
    @Query(value = """
    SELECT COALESCE(SUM(expected_per_day), 0)
    FROM (
        SELECT 
            COUNT(DISTINCT cs.user_id) AS expected_per_day
        FROM class_students cs
        JOIN classes c ON cs.class_id = c.id
        WHERE c.status = 'ACTIVE' 
          AND c.deleted_at IS NULL
          AND cs.status = 'ACTIVE' 
          AND cs.deleted_at IS NULL
          AND cs.joined_at <= :to
          AND (cs.left_at IS NULL OR cs.left_at >= :from)
    ) AS daily
    CROSS JOIN generate_series(0, :days - 1) AS day_seq
    """, nativeQuery = true)
    Long countExpectedSubmissions(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("days") long days
    );
    @Query("SELECT COUNT(s) FROM Syllabus s WHERE s.level.id = :levelId AND s.deletedAt IS NULL")
    long countSyllabusByLevelId(@Param("levelId") Long levelId);

    @Query("SELECT COUNT(c) FROM Clazz c WHERE c.syllabus.level.id = :levelId AND c.status = 'ACTIVE' AND c.deletedAt IS NULL")
    long countActiveClassesByLevelId(@Param("levelId") Long levelId);

    @Query("SELECT COUNT(ct) FROM ClassTeacher ct JOIN ct.clazz cl WHERE cl.syllabus.level.id = :levelId AND ct.status = 'ACTIVE'")
    long countTeachersByLevelId(@Param("levelId") Long levelId);

    @Query(value = "SELECT s.* FROM syllabuses s LEFT JOIN chapters ch ON ch.syllabus_id = s.id LEFT JOIN lessons l ON l.chapter_id = ch.id WHERE s.deleted_at IS NULL GROUP BY s.id", nativeQuery = true)
    List<Syllabus> findAllSyllabusesWithChaptersAndLessons();

    @Query(value = "SELECT COUNT(*) FROM chapters WHERE syllabus_id = :syllabusId AND deleted_at IS NULL", nativeQuery = true)
    long countChaptersBySyllabusId(@Param("syllabusId") Long syllabusId);

    @Query(value = "SELECT COUNT(*) FROM lessons WHERE chapter_id IN (SELECT id FROM chapters WHERE syllabus_id = :syllabusId) AND deleted_at IS NULL", nativeQuery = true)
    long countLessonsBySyllabusId(@Param("syllabusId") Long syllabusId);

    @Query(value = "SELECT COUNT(*) FROM classes WHERE syllabus_id = :syllabusId AND status = 'ACTIVE' AND deleted_at IS NULL", nativeQuery = true)
    long countActiveClassesBySyllabusId(@Param("syllabusId") Long syllabusId);

    @Query(value = "SELECT COUNT(DISTINCT cs.user_id) FROM class_students cs WHERE cs.status = 'ACTIVE' AND cs.deleted_at IS NULL", nativeQuery = true)
    long countByClassStudentsActive();

    @Query(value = "SELECT c.id, c.class_name, AVG(g.final_score * 100) as completion, AVG(g.raw_score) as avg_score, COUNT(cs.user_id) as student_count FROM classes c LEFT JOIN class_students cs ON cs.class_id = c.id LEFT JOIN submission_daily_challenges sd ON sd.user_id = cs.user_id LEFT JOIN grading_daily_challenges g ON g.submission_daily_id = sd.id WHERE c.status = 'ACTIVE' AND c.deleted_at IS NULL GROUP BY c.id ORDER BY completion DESC LIMIT :topN", nativeQuery = true)
    List<Object[]> findTopClassesByCompletionRate(@Param("topN") int topN);
}