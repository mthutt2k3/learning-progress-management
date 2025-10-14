package com.learning.progress.repository;

import com.learning.progress.entity.ClassLesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassLessonRepository extends JpaRepository<ClassLesson, Long> {

    @Query("SELECT cl FROM ClassLesson cl WHERE cl.classChapter.id = :classChapterId AND cl.clazz.id = :classId AND cl.deletedAt IS NULL ORDER BY cl.orderNumber ASC")
    List<ClassLesson> findByClassChapterIdAndClassIdAndDeletedAtIsNullOrderByOrderNumberAsc(Long classChapterId, Long classId);

    @Query("SELECT cl FROM ClassLesson cl WHERE cl.classChapter.id = :classChapterId AND cl.clazz.id = :classId AND cl.deletedAt IS NULL AND (:searchText IS NULL OR cl.classLessonName LIKE %:searchText%)")
    Page<ClassLesson> findByClassChapterIdAndClassIdAndSearchText(Long classChapterId, Long classId, String searchText, Pageable pageable);

    Optional<ClassLesson> findByIdAndDeletedAtIsNull(Long id);
}