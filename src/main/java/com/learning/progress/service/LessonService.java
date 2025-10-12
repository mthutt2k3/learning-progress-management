package com.learning.progress.service;

import com.learning.progress.dto.LessonDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.CreateLessonRequest;
import com.learning.progress.dto.syllabus.UpdateLessonRequest;

import java.util.List;

public interface LessonService {
    LessonDTO createLesson(CreateLessonRequest request);
    LessonDTO updateLesson(Long id, UpdateLessonRequest request);
    void deleteLesson(Long id);
    LessonDTO getLesson(Long id);
    DataResponse<List<LessonDTO>> getLessonList(Long chapterId, int page, int size, String searchText);
}