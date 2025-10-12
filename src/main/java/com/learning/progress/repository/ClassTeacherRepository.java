package com.learning.progress.repository;

import com.learning.progress.entity.ClassTeacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassTeacherRepository extends JpaRepository<ClassTeacher, Long> {
    @Query("SELECT ct FROM ClassTeacher ct WHERE ct.user.id = :userId AND ct.status = 'ACTIVE'")
    List<ClassTeacher> findActiveClassesByUserId(Long userId);
}