package com.learning.progress.service;

import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.entity.ClassHistory;

import java.util.List;

public interface ClassHistoryService {
    void saveClassHistory(Long classId, String actionDetails, Long actionByUserId, String actionType, String visibleToRoles);
    DataResponse<List<ClassHistory>> getClassHistory(Long classId, int page, int size, String sortBy, String sortDir);
    DataResponse<List<ClassHistory>> getClassHistoryByUser(Long userId, int page, int size, String sortBy, String sortDir);
}