package com.learning.progress.service;

import com.learning.progress.dto.ChapterDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.CreateChapterRequest;
import com.learning.progress.dto.syllabus.UpdateChapterRequest;

import java.util.List;

public interface ChapterService {
    ChapterDTO createChapter(CreateChapterRequest request);
    ChapterDTO updateChapter(Long id, UpdateChapterRequest request);
    void deleteChapter(Long id);
    ChapterDTO getChapter(Long id);
    DataResponse<List<ChapterDTO>> getChapterList(Long syllabusId, int page, int size, String searchText);
}