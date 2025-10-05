package com.learning.progress.repository;

import com.learning.progress.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    @Query(value = """
    SELECT EXISTS (
        SELECT 1
        FROM users u
        JOIN role_permissions rp ON u.role_id = rp.role_id
        JOIN permissions p ON rp.permission_id = p.id
        WHERE u.user_name = :username
          AND p.id = :permissionId
          AND p.is_active = true
          AND u.deleted_at IS NULL
          AND rp.deleted_at IS NULL
          AND p.deleted_at IS NULL
    )
""", nativeQuery = true)
    boolean existsByUsernameAndPermissionId(@Param("username") String username,
                                            @Param("permissionId") Long permissionId);
}