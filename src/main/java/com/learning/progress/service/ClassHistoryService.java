package com.learning.progress.service;

import com.learning.progress.dto.clazz.history.ClassHistoryDTO;
import com.learning.progress.dto.DataResponse;

import java.util.List;

public interface ClassHistoryService {
    void saveClassHistory(Long classId, String actionDetails, Long actionByUserId, String actionType, String visibleToRoles);

    DataResponse<List<ClassHistoryDTO>> getClassHistory(Long classId, int page, int size, String sortBy, String sortDir, String startDate, String endDate, Long actionBy);
}