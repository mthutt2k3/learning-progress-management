package com.learning.progress.service;

import com.learning.progress.dto.challenge.CreateDailyChallengeRequest;
import com.learning.progress.dto.challenge.DailyChallengeDTO;
import jakarta.validation.Valid;

import java.util.List;

public interface DailyChallengeService {

    DailyChallengeDTO createChallenge(@Valid CreateDailyChallengeRequest dto);

    List<DailyChallengeDTO> getAllChallenges(int page, int size);

    DailyChallengeDTO getChallengeById(Long id);

    DailyChallengeDTO updateChallenge(Long id, DailyChallengeDTO dto);

    void deleteChallenge(Long id, String deletedBy);
}