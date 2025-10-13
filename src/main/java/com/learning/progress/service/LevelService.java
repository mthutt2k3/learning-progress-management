package com.learning.progress.service;

import com.learning.progress.dto.request.CreateLevelRequest;
import com.learning.progress.dto.request.UpdateLevelOrderRequest;
import com.learning.progress.dto.request.UpdateLevelRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.response.LevelDetailsResponse;
import com.learning.progress.dto.response.LevelListResponse;

import java.util.List;

public interface LevelService {
    DataResponse<List<LevelListResponse>> getAllLevels(int page, int size, String text, List<Boolean> status, String sortBy, String sortDir);
    LevelDetailsResponse getLevelDetails(Long id);
    void createLevel(CreateLevelRequest request);
    void updateLevel(Long id, UpdateLevelRequest request);
    void toggleLevelStatus(Long id);
    void bulkUpdateLevels(List<UpdateLevelOrderRequest> requests);
}
