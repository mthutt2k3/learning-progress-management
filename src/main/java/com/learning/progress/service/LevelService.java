package com.learning.progress.service;

import com.learning.progress.dto.level.SyncLevelRequest;
import com.learning.progress.dto.level.UpdateLevelRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.level.LevelDetailsResponse;

import java.util.List;

public interface LevelService {
    DataResponse<List<LevelDetailsResponse>> getAllLevels(int page, int size, String text);
    LevelDetailsResponse getLevelDetails(Long id);
    void updateLevel(Long id, UpdateLevelRequest request);
    List<LevelDetailsResponse> bulkUpdateLevels(List<SyncLevelRequest> requests);

    void publishAllLevels();

    void draftAllLevels();

    DataResponse<List<LevelDetailsResponse>> getAllPublishLevels(int page, int size, String text);
}
