package com.learning.progress.repository;

import com.learning.progress.common.CommonStatus;
import com.learning.progress.entity.StudentLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentLevelRepository extends JpaRepository<StudentLevel, Long> {
    Optional<StudentLevel> findByUserIdAndStatus(Long userId, String status);
}