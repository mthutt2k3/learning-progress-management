package com.learning.progress.service;

import com.learning.progress.dto.SyllabusDTO;
import com.learning.progress.dto.*;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.CreateSyllabusRequest;
import com.learning.progress.dto.syllabus.UpdateSyllabusRequest;

import java.util.List;

public interface SyllabusService {
    SyllabusDTO createSyllabus(CreateSyllabusRequest request);
    SyllabusDTO updateSyllabus(Long id, UpdateSyllabusRequest request);
    void deleteSyllabus(Long id);
    SyllabusDTO getSyllabus(Long id);
    DataResponse<List<SyllabusDTO>> getSyllabusList(int page, int size, String searchText);
}