package com.learning.progress.repository;

import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
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
    Optional<User> findByUserName(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByResetPasswordToken(String resetPasswordToken);

    @Query("""
    SELECT u FROM User u
    WHERE u.role.name IN :roles
      AND (:statuses IS NULL OR u.status IN :statuses)
      AND (:text IS NULL OR 
           (LOWER(u.firstName) LIKE LOWER(CONCAT('%', CAST(:text AS string), '%'))
         OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', CAST(:text AS string), '%'))
         OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:text AS string), '%'))))
""")
    Page<User> findByRoleNameInAndStatusInAndSearchText(
            @Param("roles") List<RoleName> roles,
            @Param("statuses") List<UserStatus> statuses,
            @Param("text") String text,
            Pageable pageable);



    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE LOWER(u.userName) = LOWER(:userName)")
    boolean existsByUserName(String userName);

    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :text, '%'))")
    Page<User> findByText(@Param("text") String text, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.status IN :statuses")
    Page<User> findByStatusIn(@Param("statuses") List<UserStatus> statuses, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role.name IN :roleNames")
    Page<User> findByRoleNameIn(@Param("roleNames") List<RoleName> roleNames, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.status IN :statuses AND u.role.name IN :roleNames")
    Page<User> findByStatusInAndRoleNameIn(@Param("statuses") List<UserStatus> statuses, @Param("roleNames") List<RoleName> roleNames, Pageable pageable);

    @Query("SELECT u FROM User u WHERE (LOWER(u.email) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :text, '%'))) AND u.status IN :statuses")
    Page<User> findByTextAndStatusIn(@Param("text") String text, @Param("statuses") List<UserStatus> statuses, Pageable pageable);

    @Query("SELECT u FROM User u WHERE (LOWER(u.email) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :text, '%'))) AND u.role.name IN :roleNames")
    Page<User> findByTextAndRoleNameIn(@Param("text") String text, @Param("roleNames") List<RoleName> roleNames, Pageable pageable);

    @Query("SELECT u FROM User u WHERE (LOWER(u.email) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :text, '%'))) AND u.status IN :statuses AND u.role.name IN :roleNames")
    Page<User> findByTextAndStatusInAndRoleNameIn(@Param("text") String text, @Param("statuses") List<UserStatus> statuses, @Param("roleNames") List<RoleName> roleNames, Pageable pageable);
}
