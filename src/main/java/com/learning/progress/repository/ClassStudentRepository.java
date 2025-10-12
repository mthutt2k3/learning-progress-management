package com.learning.progress.repository;

import com.learning.progress.entity.ClassStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClassStudentRepository extends JpaRepository<ClassStudent, Long> {
    Optional<ClassStudent> findByUserIdAndStatus(Long userId, String status);
}