package com.learning.progress.service;

import com.learning.progress.dto.LessonDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.SyncLessonRequest;

import java.util.List;

public interface LessonService {
    LessonDTO getLesson(Long id);
    DataResponse<List<LessonDTO>> getLessonList(Long chapterId, int page, int size, String searchText);
    List<LessonDTO> syncLessons(Long chapterId, List<SyncLessonRequest> request);

}