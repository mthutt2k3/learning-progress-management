package com.learning.progress.service;

import com.learning.progress.dto.request.UpdateLevelOrderRequest;
import com.learning.progress.dto.request.UpdateLevelRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.response.LevelDetailsResponse;

import java.util.List;

public interface LevelService {
    DataResponse<List<LevelDetailsResponse>> getAllLevels(int page, int size, String text, List<Boolean> status);
    LevelDetailsResponse getLevelDetails(Long id);
    void updateLevel(Long id, UpdateLevelRequest request);
    void toggleLevelStatus(Long id);
    List<LevelDetailsResponse> bulkUpdateLevels(List<UpdateLevelOrderRequest> requests);
}
