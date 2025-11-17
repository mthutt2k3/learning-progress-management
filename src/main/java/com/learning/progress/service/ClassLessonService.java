package com.learning.progress.service;

import com.learning.progress.dto.clazz.lesson.ClassLessonDTO;
import com.learning.progress.dto.clazz.lesson.SyncClassLessonRequest;
import com.learning.progress.dto.DataResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ClassLessonService {

    List<ClassLessonDTO> syncClassLessons(Long classChapterId, List<SyncClassLessonRequest> request);

    ClassLessonDTO getClassLesson(Long id);

    DataResponse<List<ClassLessonDTO>> getClassLessonList(Long classChapterId, int page, int size, String searchText);

    List<ClassLessonDTO> importLessonsInClassFromExcel(MultipartFile file, Long classId);

    String getLessonInClassTemplateSasUrl();

    byte[] validateClassLessonImportFile(Long classId, MultipartFile file);

    byte[] generateLessonInClassImportTemplate();
}