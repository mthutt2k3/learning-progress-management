package com.learning.progress.service;

import com.learning.progress.dto.chapter.ChapterDTO;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.chapter.SyncChapterRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChapterService {
    ChapterDTO getChapter(Long id);
    DataResponse<List<ChapterDTO>> getChapterList(Long syllabusId, int page, int size, String searchText);
    List<ChapterDTO> syncChapters(Long syllabusId, List<SyncChapterRequest> request);
    List<ChapterDTO> importChaptersFromExcel(Long syllabusId, MultipartFile file);
    byte[] generateChapterImportTemplate();
    String getChapterTemplateSasUrl();
    byte[] downloadChapterValidationFile(Long syllabusId, MultipartFile file);
}