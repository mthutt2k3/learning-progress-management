package com.learning.progress.service;

import com.learning.progress.dto.clazz.chapter.ClassChapterDTO;
import com.learning.progress.dto.clazz.chapter.SyncClassChapterRequest;
import com.learning.progress.dto.DataResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ClassChapterService {

    List<ClassChapterDTO> syncClassChapters(Long classId, List<SyncClassChapterRequest> request);

    ClassChapterDTO getClassChapter(Long id);

    DataResponse<List<ClassChapterDTO>> getClassChapterList(Long classId, int page, int size, String searchText);

    byte[] generateClassChaptersImportTemplate();

    String getClassChaptersTemplateSasUrl();

    byte[] downloadClassChapterValidationImportFile(Long classId, MultipartFile file);

    List<ClassChapterDTO> importClassChaptersFromExcel(Long classId, MultipartFile file);
}