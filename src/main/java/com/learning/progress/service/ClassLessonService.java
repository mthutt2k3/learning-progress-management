package com.learning.progress.service;

import com.learning.progress.dto.clazz.ClassLessonDTO;
import com.learning.progress.dto.clazz.SyncClassLessonRequest;
import com.learning.progress.dto.response.DataResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public interface ClassLessonService {

    List<ClassLessonDTO> syncClassLessons(Long classChapterId, List<SyncClassLessonRequest> request);

    ClassLessonDTO getClassLesson(Long id);

    DataResponse<List<ClassLessonDTO>> getClassLessonList(Long classChapterId, int page, int size, String searchText);

    void exportClassLessonsToExcel(Long classChapterId, OutputStream outputStream);

    List<ClassLessonDTO> importLessonsInClassFromExcel(MultipartFile file, Long classId);

    String getLessonInClassTemplateSasUrl();

    byte[] generateLessonInClassImportTemplate();
}