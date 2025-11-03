package com.learning.progress.dto.submission;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SubmissionsByChallengeResponse {
    private List<StudentSubmissionDTO> submissions;
    private long submittedCount;
}
