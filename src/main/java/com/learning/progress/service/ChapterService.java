package com.learning.progress.service;

import com.learning.progress.dto.ChapterDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.SyncChapterRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChapterService {
    ChapterDTO getChapter(Long id);
    DataResponse<List<ChapterDTO>> getChapterList(Long syllabusId, int page, int size, String searchText);
    List<ChapterDTO> syncChapters(Long syllabusId, List<SyncChapterRequest> request);
    List<ChapterDTO> importChaptersFromExcel(MultipartFile file);
    byte[] generateChapterImportTemplate();
    String getChapterTemplateSasUrl();
}