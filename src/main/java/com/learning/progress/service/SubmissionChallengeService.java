package com.learning.progress.service;

import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.StudentChallengeListDTO;
import com.learning.progress.dto.submission.StudentSubmissionDTO;
import com.learning.progress.entity.DailyChallenge;

import java.util.List;

public interface SubmissionChallengeService {
    void createTemporarySubmissionsAsync(DailyChallenge challenge);

    DataResponse<List<StudentChallengeListDTO>> getAllChallengesForStudent(Long classId, int page, int size, String text);

    DataResponse<List<StudentSubmissionDTO>> getSubmissionsByChallenge(Long challengeId, int page, int size, String text, String sortBy, String sortDir);

    void autoSubmitExpiredSubmissions();

    int detectAndMarkLateSubmissions();
}
