package com.learning.progress.service;

import com.learning.progress.entity.DailyChallenge;

public interface SubmissionChallengeService {
    void createTemporarySubmissionsAsync(DailyChallenge challenge);
}
