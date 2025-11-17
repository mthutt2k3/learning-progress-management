package com.learning.progress.repository;

import com.learning.progress.common.ClassStudentStatus;
import com.learning.progress.entity.ClassStudent;
import com.learning.progress.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ClassStudentRepository extends JpaRepository<ClassStudent, Long> {
    Optional<ClassStudent> findByUserIdAndStatus(Long userId, ClassStudentStatus status);

    @Query("SELECT cs FROM ClassStudent cs WHERE cs.clazz.id = :classId AND cs.status IN :statuses " +
            "AND (LOWER(cs.user.fullName) LIKE LOWER(CONCAT('%', :text, '%')) " +
            "OR LOWER(cs.user.email) LIKE LOWER(CONCAT('%', :text, '%')) " +
            "OR LOWER(cs.user.userName) LIKE LOWER(CONCAT('%', :text, '%')))")
    Page<ClassStudent> findByClassIdAndText(@Param("classId") Long classId,
                                            @Param("text") String text,
                                            @Param("statuses") List<ClassStudentStatus> statuses,
                                            Pageable pageable);

    @Query("SELECT cs FROM ClassStudent cs WHERE cs.clazz.id = :classId AND cs.status IN :statuses")
    Page<ClassStudent> findByClassIdAndStatus(@Param("classId") Long classId,
                                              @Param("statuses") List<ClassStudentStatus> statuses,
                                              Pageable pageable);

    @Query("SELECT cs FROM ClassStudent cs WHERE cs.clazz.id = :clazzId AND cs.user.id = :userId")
    Optional<ClassStudent> findByClazzIdAndUserId(Long clazzId, Long userId);

    @Query("SELECT cs FROM ClassStudent cs WHERE cs.clazz.id = :classId ORDER BY cs.user.fullName")
    List<ClassStudent> findByClazzId(@Param("classId") Long classId);

    @Query("SELECT cs FROM ClassStudent cs WHERE cs.user.id = :userId")
    List<ClassStudent> findByUserId(@Param("userId") Long userId);

    Optional<ClassStudent> findByUserIdAndClazzIdAndStatus(Long userId, Long classId, ClassStudentStatus classStudentStatus);

    // Count active students in a class
    @Query("SELECT COUNT(cs) FROM ClassStudent cs " +
            "WHERE cs.clazz.id = :classId " +
            "AND cs.status = :status " +
            "AND cs.deletedAt IS NULL")
    long countByClassIdAndStatus(
            @Param("classId") Long classId,
            @Param("status") ClassStudentStatus status
    );

    // Find user IDs of active students in a class for a given list of user IDs
    @Query("SELECT cs.user.id FROM ClassStudent cs " +
            "WHERE cs.clazz.id = :classId " +
            "AND cs.user.id IN :userIds " +
            "AND cs.status = :status " +
            "AND cs.deletedAt IS NULL")
    List<Long> findUserIdsByClassIdAndUserIdInAndStatus(
            @Param("classId") Long classId,
            @Param("userIds") List<Long> userIds,
            @Param("status") ClassStudentStatus status
    );

    // Find ClassStudent entities by class ID, user IDs, and status
    @Query("SELECT cs FROM ClassStudent cs " +
            "WHERE cs.clazz.id = :classId " +
            "AND cs.user.id IN :userIds " +
            "AND cs.status = :status " +
            "AND cs.deletedAt IS NULL")
    List<ClassStudent> findByClassIdAndUserIdInAndStatus(
            @Param("classId") Long classId,
            @Param("userIds") List<Long> userIds,
            @Param("status") ClassStudentStatus status
    );

    // Trong ClassStudentRepository
    @Query("SELECT cs FROM ClassStudent cs " +
            "WHERE cs.user.id = :userId AND cs.status = :status " +
            "ORDER BY cs.joinedAt DESC " +
            "LIMIT 1")
    Optional<ClassStudent> findFirstByUserIdAndStatusOrderByJoinedAtDesc(@Param("userId") Long userId, @Param("status") ClassStudentStatus status);

    List<ClassStudent> findByUserIdInAndStatus(List<Long> userIds, ClassStudentStatus status);

    boolean existsByUser_IdAndClazz_IdAndStatus(Long id, Long classId, ClassStudentStatus classStudentStatus);

    int countByClazzIdAndDeletedAtIsNull(Long classId);

    @Query("SELECT cs.user FROM ClassStudent cs WHERE cs.clazz.id = :classId AND cs.status = :status")
    List<User> findUsersByClazzIdAndStatus(@Param("classId") Long classId, @Param("status") ClassStudentStatus status);
}