package com.learning.progress.service;

import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.*;
import jakarta.validation.Valid;

import java.time.OffsetDateTime;
import java.util.List;

public interface DailyChallengeService {

    DailyChallengeResponse createChallenge(@Valid CreateDailyChallengeRequest dto);

    DataResponse<List<DailyChallengeListDTO>> getAllChallenges(Long classId, int page, int size, String text, String sortBy, String sortDir);

    DailyChallengeResponse getChallengeById(Long id);

    DailyChallengeResponse updateChallenge(Long id, @Valid UpdateDailyChallengeDTO dto);

    void deleteChallenge(Long id);

    DailyChallengeResponse publishChallenge(Long id);

    DailyChallengeHierarchyDTO getChallengeHierarchy(Long challengeId);

    byte[] exportChallengeWorksheet(Long challengeId);

    void autoUpdateChallengeStatus(OffsetDateTime now);
}