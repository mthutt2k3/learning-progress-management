package com.learning.progress.service;

import com.learning.progress.dto.ai.GradingWritingRequest;
import com.learning.progress.dto.ai.GradingWritingResponse;
import com.learning.progress.dto.ai.PronunciationAssessmentRequest;
import com.learning.progress.dto.ai.PronunciationAssessmentResponse;

public interface AiFeedbackService {


    GradingWritingResponse gradeWriting(GradingWritingRequest request);

    PronunciationAssessmentResponse assessPronunciation(PronunciationAssessmentRequest request);

}
