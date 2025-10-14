package com.learning.progress.service;

import com.learning.progress.dto.clazz.ClassLessonDTO;
import com.learning.progress.dto.clazz.SyncClassLessonRequest;
import com.learning.progress.dto.response.DataResponse;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public interface ClassLessonService {

    List<ClassLessonDTO> syncClassLessons(Long classId, Long classChapterId, List<SyncClassLessonRequest> request);

    ClassLessonDTO getClassLesson(Long id);

    DataResponse<List<ClassLessonDTO>> getClassLessonList(Long classId, Long classChapterId, int page, int size, String searchText);

    void exportClassLessonsToExcel(Long classChapterId, OutputStream outputStream);

    void importClassLessonsFromExcel(Long classChapterId, InputStream inputStream);

    void downloadImportTemplate(OutputStream outputStream);
}