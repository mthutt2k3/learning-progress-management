package com.learning.progress.service;

import com.learning.progress.dto.clazz.ClassChapterDTO;
import com.learning.progress.dto.clazz.SyncClassChapterRequest;
import com.learning.progress.dto.response.DataResponse;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public interface ClassChapterService {

    List<ClassChapterDTO> syncClassChapters(Long classId, List<SyncClassChapterRequest> request);

    ClassChapterDTO getClassChapter(Long id);

    DataResponse<List<ClassChapterDTO>> getClassChapterList(Long classId, int page, int size, String searchText);

    void exportClassChaptersToExcel(Long classId, OutputStream outputStream);

    void importClassChaptersFromExcel(Long classId, InputStream inputStream);

    void downloadImportTemplate(OutputStream outputStream);
}