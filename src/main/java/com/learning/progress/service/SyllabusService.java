package com.learning.progress.service;

import com.learning.progress.dto.SyllabusDTO;
import com.learning.progress.dto.SyllabusDetailDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.CreateSyllabusRequest;
import com.learning.progress.dto.syllabus.UpdateSyllabusRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SyllabusService {
    SyllabusDTO createSyllabus(CreateSyllabusRequest request);
    SyllabusDTO updateSyllabus(Long id, UpdateSyllabusRequest request);
    void deleteSyllabus(Long id);
    SyllabusDetailDTO getSyllabusDetail(Long id, String include);
    DataResponse<List<SyllabusDTO>> getSyllabusList(int page, int size, String searchText);
    byte[] generateSyllabusImportTemplate();
    String getSyllabusTemplateSasUrl();
    List<SyllabusDTO> importSyllabusFromExcel(MultipartFile file);
}