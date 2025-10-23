package com.learning.progress.service;

import com.learning.progress.common.ClassStatus;
import com.learning.progress.dto.clazz.ClassDTO;
import com.learning.progress.dto.clazz.ClassOverviewDTO;
import com.learning.progress.dto.clazz.CreateClassRequest;
import com.learning.progress.dto.clazz.UpdateClassRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.clazz.history.ClassHistoryDTO;

import java.util.List;

public interface ClassService {
    ClassOverviewDTO getClassOverview(Long id);

    DataResponse<List<ClassHistoryDTO>> getClassHistory(Long classId, int page, int size, String sortBy, String sortDir, String startDate, String endDate, Long actionBy);

    ClassDTO createClass(CreateClassRequest request);

    ClassDTO getClass(Long id);

    DataResponse<List<ClassDTO>> getClassList(int page, int size, String searchText, List<ClassStatus> status, Long syllabusId, String sortBy, String sortDir);

    ClassDTO updateClass(Long id, UpdateClassRequest request);

    String changeClassStatusManually(Long id, String status);

    void deleteClass(Long id);

}