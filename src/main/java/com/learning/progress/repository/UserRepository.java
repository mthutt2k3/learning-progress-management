package com.learning.progress.repository;

import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.level.LevelInfo;
import com.learning.progress.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserNameAndDeletedAtIsNull(String username);
    Optional<User> findByIdAndDeletedAtIsNull(Long id);


    @Query("""
    SELECT u FROM User u
    WHERE u.role.name IN :roles
      AND (:statuses IS NULL OR u.status IN :statuses)
      AND (:text IS NULL OR 
           (LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:text AS string), '%'))
         OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:text AS string), '%'))
         OR LOWER(u.userName) LIKE LOWER(CONCAT('%', CAST(:text AS string), '%'))))
         AND u.deletedAt is null
""")
    Page<User> findByRoleNameInAndStatusInAndSearchText(
            @Param("roles") List<RoleName> roles,
            @Param("statuses") List<UserStatus> statuses,
            @Param("text") String text,
            Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE LOWER(u.userName) = LOWER(:userName)")
    boolean existsByUserName(String userName);

    @Query("SELECT u FROM User u WHERE u.status IN :statuses AND u.deletedAt IS NULL")
    Page<User> findByStatusIn(@Param("statuses") List<UserStatus> statuses, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role.name IN :roleNames AND u.deletedAt IS NULL")
    Page<User> findByRoleNameIn(@Param("roleNames") List<RoleName> roleNames, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.status IN :statuses AND u.role.name IN :roleNames AND u.deletedAt IS NULL")
    Page<User> findByStatusInAndRoleNameIn(@Param("statuses") List<UserStatus> statuses, @Param("roleNames") List<RoleName> roleNames, Pageable pageable);

    @Query("SELECT NEW com.learning.progress.dto.level.LevelInfo(sl.level.id, sl.level.levelCode , sl.level.levelName) " +
            "FROM StudentLevel sl WHERE sl.user.id = :userId AND sl.status = 'ACTIVE'")
    Optional<LevelInfo> findActiveLevelInfoByUserId(Long userId);

    @Query("SELECT u FROM User u WHERE (LOWER(u.email) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(u.userName) LIKE LOWER(CONCAT('%', :text, '%'))) AND u.deletedAt IS NULL")
    Page<User> findByText(@Param("text") String text, Pageable pageable);

    @Query("SELECT u FROM User u WHERE (LOWER(u.email) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(u.userName) LIKE LOWER(CONCAT('%', :text, '%'))) AND u.status IN :statuses AND u.deletedAt IS NULL")
    Page<User> findByTextAndStatusIn(@Param("text") String text, @Param("statuses") List<UserStatus> statuses, Pageable pageable);

    @Query("SELECT u FROM User u WHERE (LOWER(u.email) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(u.userName) LIKE LOWER(CONCAT('%', :text, '%'))) AND u.role.name IN :roleNames AND u.deletedAt IS NULL")
    Page<User> findByTextAndRoleNameIn(@Param("text") String text, @Param("roleNames") List<RoleName> roleNames, Pageable pageable);

    @Query("SELECT u FROM User u WHERE (LOWER(u.email) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(u.userName) LIKE LOWER(CONCAT('%', :text, '%'))) AND u.status IN :statuses AND u.role.name IN :roleNames AND u.deletedAt IS NULL")
    Page<User> findByTextAndStatusInAndRoleNameIn(@Param("text") String text, @Param("statuses") List<UserStatus> statuses, @Param("roleNames") List<RoleName> roleNames, Pageable pageable);

    @Query("""
    SELECT u FROM User u 
    WHERE u.role.name IN :roleNames 
    AND u.status IN :statuses 
    AND (:searchText IS NULL OR :searchText = '' OR 
         LOWER(u.email) LIKE LOWER(CONCAT('%', :searchText, '%')) OR 
         LOWER(u.fullName) LIKE LOWER(CONCAT('%', :searchText, '%')) OR 
         LOWER(u.userName) LIKE LOWER(CONCAT('%', :searchText, '%')))
    ORDER BY u.createdAt DESC
""")
    List<User> findByRoleNameInAndStatusInAndSearchText(
            @Param("roleNames") List<RoleName> roleNames,
            @Param("statuses") List<UserStatus> statuses,
            @Param("searchText") String searchText
    );

    @Query("SELECT u FROM User u WHERE LOWER(u.userName) IN :userNames")
    List<User> findByUserNameInIgnoreCase(@Param("userNames") List<String> userNames);

    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    List<User> findAllByIdInAndDeletedAtIsNull(List<Long> userIds);

    @Query(value = """
    SELECT DISTINCT ct.user_id
    FROM class_students cs
    JOIN class_teachers ct ON cs.class_id = ct.class_id
    WHERE cs.user_id = :studentId
    AND cs.status = 'ACTIVE'
    AND cs.deleted_at IS NULL
    AND ct.status = 'ACTIVE'
    AND ct.deleted_at IS NULL
    """, nativeQuery = true)
    List<Long> findTeacherIdsByStudentId(@Param("studentId") Long studentId);

    @Query(value = """
    SELECT u.id
    FROM users u
    JOIN roles r ON u.role_id = r.id
    WHERE r.name IN ('MANAGER')
    AND u.status = 'ACTIVE'
    AND u.deleted_at IS NULL
    """, nativeQuery = true)
    List<Long> findAllManagerIds();

    @Query(value = """
    SELECT u.id
    FROM users u
    JOIN roles r ON u.role_id = r.id
    WHERE r.name IN ('ADMIN')
    AND u.status = 'ACTIVE'
    AND u.deleted_at IS NULL
    """, nativeQuery = true)
    List<Long> findAllAdminIds();
}
