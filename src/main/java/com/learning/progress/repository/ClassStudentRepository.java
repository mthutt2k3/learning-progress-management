package com.learning.progress.repository;

import com.learning.progress.common.ClassStudentStatus;
import com.learning.progress.entity.ClassStudent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClassStudentRepository extends JpaRepository<ClassStudent, Long> {
    Optional<ClassStudent> findByUserIdAndStatus(Long userId, ClassStudentStatus status);

    @Query("SELECT cs FROM ClassStudent cs WHERE cs.clazz.id = :classId AND cs.status = :status " +
            "AND (cs.user.fullName LIKE %:text% OR cs.user.email LIKE %:text%)")
    Page<ClassStudent> findByClassIdAndText(Long classId, String text, ClassStudentStatus status, Pageable pageable);

    @Query("SELECT cs FROM ClassStudent cs WHERE cs.clazz.id = :classId AND cs.status = :status")
    Page<ClassStudent> findByClassIdAndStatus(Long classId, ClassStudentStatus status, Pageable pageable);

    @Query("SELECT cs FROM ClassStudent cs WHERE cs.clazz.id = :clazzId AND cs.user.id = :userId")
    Optional<ClassStudent> findByClazzIdAndUserId(Long clazzId, Long userId);

    @Query("SELECT COUNT(cs) > 0 FROM ClassStudent cs WHERE cs.clazz.id = :clazzId AND cs.user.id = :userId")
    boolean existsByClassIdAndUserId(Long clazzId, Long userId);

    @Query("SELECT cs FROM ClassStudent cs WHERE cs.clazz.id = :classId ORDER BY cs.user.fullName")
    List<ClassStudent> findByClazzId(@Param("classId") Long classId);

    @Query("SELECT cs FROM ClassStudent cs WHERE cs.user.id = :userId")
    List<ClassStudent> findByUserId(@Param("userId") Long userId);

    @Query("SELECT cs.user.id FROM ClassStudent cs WHERE cs.clazz.id = :classId AND cs.user.id IN :userIds")
    List<Long> findUserIdsByClassIdAndUserIdIn(@Param("classId") Long classId, @Param("userIds") List<Long> userIds);

    // Add this method
    @Query("SELECT cs.clazz.id, cs.user.id FROM ClassStudent cs " +
            "WHERE cs.clazz.id IN :classIds AND cs.user.id IN :userIds")
    List<Object[]> findExistingClassStudentPairs(
            @Param("classIds") List<Long> classIds,
            @Param("userIds") List<Long> userIds);

    Optional<ClassStudent> findByUserIdAndClazzIdAndStatus(Long userId, Long classId, ClassStudentStatus classStudentStatus);
}