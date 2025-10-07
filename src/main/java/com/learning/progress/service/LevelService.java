package com.learning.progress.service;

import com.learning.progress.dto.request.CreateLevelRequest;
import com.learning.progress.dto.request.UpdateLevelRequest;
import com.learning.progress.dto.response.LevelDetailsResponse;
import com.learning.progress.dto.response.LevelListResponse;

import java.util.List;

public interface LevelService {
    List<LevelListResponse> getAllLevels();
    LevelDetailsResponse getLevelDetails(Long id);
    void createLevel(CreateLevelRequest request);
    void updateLevel(Long id, UpdateLevelRequest request);
    void toggleLevelStatus(Long id);
}
