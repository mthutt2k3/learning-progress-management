package com.learning.progress.service;

import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.CreateDailyChallengeRequest;
import com.learning.progress.dto.challenge.DailyChallengeResponse;
import com.learning.progress.dto.challenge.DailyChallengeListDTO;
import com.learning.progress.dto.challenge.UpdateDailyChallengeDTO;
import jakarta.validation.Valid;

import java.util.List;

public interface DailyChallengeService {

    DailyChallengeResponse createChallenge(@Valid CreateDailyChallengeRequest dto);

    DataResponse<List<DailyChallengeListDTO>> getAllChallenges(Long classId, int page, int size, String text, String sortBy, String sortDir);

    DailyChallengeResponse getChallengeById(Long id);

    DailyChallengeResponse updateChallenge(Long id, @Valid UpdateDailyChallengeDTO dto);

    void deleteChallenge(Long id);

    DailyChallengeResponse updateChallengeStatus(Long id, ChallengeStatus challengeStatus);
}