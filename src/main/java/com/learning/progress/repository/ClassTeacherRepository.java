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

    Collection<ClassTeacher> findByUser_Id(Long userId);

    boolean existsByUser_IdAndClazz_Id(Long id, Long classId);

    @Query("SELECT ct FROM ClassTeacher ct WHERE ct.clazz.id = :classId AND ct.status = :status " +
            "AND (LOWER(ct.user.userName) LIKE LOWER(CONCAT('%', :text, '%')) " +
            "OR LOWER(ct.user.fullName) LIKE LOWER(CONCAT('%', :text, '%')) " +
            "OR LOWER(ct.user.email) LIKE LOWER(CONCAT('%', :text, '%')))")
    Page<ClassTeacher> findByClassIdAndText(@Param("classId") Long classId, @Param("text") String text,
                                            @Param("status") ClassTeacherStatus status, Pageable pageable);

    Page<ClassTeacher> findByClazzIdAndStatus(Long classId, ClassTeacherStatus status, Pageable pageable);

    Optional<ClassTeacher> findByClazzIdAndUserId(Long classId, Long userId);

    boolean existsByClazzIdAndUserId(Long classId, Long userId);

    boolean existsByClazz_IdAndUser_IdAndStatus(Long classId, Long userId, ClassTeacherStatus status);

    @Query("SELECT ct FROM ClassTeacher ct WHERE ct.clazz.id = :classId")
    List<ClassTeacher> findByClazzId(@Param("classId") Long classId);

    boolean existsByClazzIdAndRoleInClass(Long clazzId, RoleInClass roleInClass);

    @Query("SELECT ct.user.id FROM ClassTeacher ct WHERE ct.clazz.id = :clazzId AND ct.user.id IN :userIds")
    List<Long> findUserIdsByClazzIdAndUserIdIn(@Param("clazzId") Long clazzId, @Param("userIds") List<Long> userIds);

    @Query("SELECT ct FROM ClassTeacher ct WHERE ct.user.id = :userId AND ct.clazz.id = :clazzId")
    Optional<ClassTeacher> findByUserIdAndClazzId(@Param("userId") Long userId,
                                                  @Param("clazzId") Long clazzId);

    @Query("SELECT ct FROM ClassTeacher ct WHERE ct.user.id = :userId AND ct.status = :status")
    List<ClassTeacher> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") ClassTeacherStatus status);

    @Query("SELECT ct FROM ClassTeacher ct WHERE ct.user.id = :userId AND ct.clazz.id = :clazzId AND ct.status = :status")
    Optional<ClassTeacher> findByUserIdAndClazzIdAndStatus(
            @Param("userId") Long userId,
            @Param("clazzId") Long clazzId,
            @Param("status") ClassTeacherStatus status);
}