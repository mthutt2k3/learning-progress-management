package com.learning.progress.repository;

import com.learning.progress.common.ClassTeacherStatus;
import com.learning.progress.common.RoleInClass;
import com.learning.progress.entity.ClassTeacher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassTeacherRepository extends JpaRepository<ClassTeacher, Long> {
    @Query("SELECT ct FROM ClassTeacher ct WHERE ct.user.id = :userId AND ct.status = 'ACTIVE'")
    List<ClassTeacher> findActiveClassesByUserId(Long userId);

    @Query("SELECT ct FROM ClassTeacher ct WHERE ct.clazz.id = :classId AND ct.status IN :statuses " +
            "AND (LOWER(ct.user.userName) LIKE LOWER(CONCAT('%', :text, '%')) " +
            "OR LOWER(ct.user.fullName) LIKE LOWER(CONCAT('%', :text, '%')) " +
            "OR LOWER(ct.user.email) LIKE LOWER(CONCAT('%', :text, '%')))")
    Page<ClassTeacher> findByClassIdAndText(@Param("classId") Long classId,
                                            @Param("text") String text,
                                            @Param("statuses") List<ClassTeacherStatus> statuses,
                                            Pageable pageable);

    Page<ClassTeacher> findByClazzIdAndStatusIn(Long classId, List<ClassTeacherStatus> statuses, Pageable pageable);

    Optional<ClassTeacher> findByClazzIdAndUserId(Long classId, Long userId);

    boolean existsByClazz_IdAndUser_IdAndStatus(Long classId, Long userId, ClassTeacherStatus status);

    @Query("SELECT ct FROM ClassTeacher ct WHERE ct.clazz.id = :classId")
    List<ClassTeacher> findByClazzId(@Param("classId") Long classId);

    @Query("SELECT ct FROM ClassTeacher ct WHERE ct.user.id = :userId AND ct.status = :status")
    List<ClassTeacher> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") ClassTeacherStatus status);

    @Query("SELECT ct FROM ClassTeacher ct WHERE ct.user.id = :userId AND ct.clazz.id = :clazzId AND ct.status = :status")
    Optional<ClassTeacher> findByUserIdAndClazzIdAndStatus(
            @Param("userId") Long userId,
            @Param("clazzId") Long clazzId,
            @Param("status") ClassTeacherStatus status);

    // Check if a class has an active teacher with a specific role
    @Query("SELECT COUNT(ct) > 0 FROM ClassTeacher ct " +
            "WHERE ct.clazz.id = :classId " +
            "AND ct.roleInClass = :roleInClass " +
            "AND ct.status = :status " +
            "AND ct.deletedAt IS NULL")
    boolean existsByClazzIdAndRoleInClassAndStatus(
            @Param("classId") Long classId,
            @Param("roleInClass") RoleInClass roleInClass,
            @Param("status") ClassTeacherStatus status
    );

    // Count active teachers with a specific role in a class
    @Query("SELECT COUNT(ct) FROM ClassTeacher ct " +
            "WHERE ct.clazz.id = :classId " +
            "AND ct.roleInClass = :roleInClass " +
            "AND ct.status = :status " +
            "AND ct.deletedAt IS NULL")
    long countByClazzIdAndRoleInClassAndStatus(
            @Param("classId") Long classId,
            @Param("roleInClass") RoleInClass roleInClass,
            @Param("status") ClassTeacherStatus status
    );

    // Find user IDs of active teachers in a class for a given list of user IDs
    @Query("SELECT ct.user.id FROM ClassTeacher ct " +
            "WHERE ct.clazz.id = :classId " +
            "AND ct.user.id IN :userIds " +
            "AND ct.status = :status " +
            "AND ct.deletedAt IS NULL")
    List<Long> findUserIdsByClazzIdAndUserIdInAndStatus(
            @Param("classId") Long classId,
            @Param("userIds") List<Long> userIds,
            @Param("status") ClassTeacherStatus status
    );

    // Find ClassTeacher entities by class ID, user IDs, and status
    @Query("SELECT ct FROM ClassTeacher ct " +
            "WHERE ct.clazz.id = :classId " +
            "AND ct.user.id IN :userIds " +
            "AND ct.status = :status " +
            "AND ct.deletedAt IS NULL")
    List<ClassTeacher> findByClazzIdAndUserIdInAndStatus(
            @Param("classId") Long classId,
            @Param("userIds") List<Long> userIds,
            @Param("status") ClassTeacherStatus status
    );

    boolean existsByUser_IdAndClazz_IdAndStatus(Long id, Long classId, ClassTeacherStatus classTeacherStatus);

    // Trong ClassUserRepository
    List<ClassTeacher> findByClazzIdAndRoleInClassAndDeletedAtIsNull(Long clazzId, RoleInClass role);

}