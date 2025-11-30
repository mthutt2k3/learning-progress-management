package com.learning.progress.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
import com.learning.progress.common.QuestionType;
import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.dto.submission.*;
import com.learning.progress.exception.GlobalExceptionHandler;
import com.learning.progress.service.SubmissionQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SubmissionQuestionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private SubmissionQuestionService submissionQuestionService;
    @InjectMocks private SubmissionQuestionController controller;

    private final Long SUBMISSION_ID = 200L;
    private final Long SUBMISSION_QUESTION_ID = 400L;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("1. POST /submission/{id} → 200 OK")
    void saveSubmission_success() throws Exception {
        doNothing().when(submissionQuestionService).saveSubmission(eq(SUBMISSION_ID), any());

        SaveSubmissionRequest request = SaveSubmissionRequest.builder()
                .saveAsDraft(true)
                .questionAnswers(List.of())
                .build();

        mockMvc.perform(post("/api/v1/submission/{submissionChallengeId}", SUBMISSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));

        verify(submissionQuestionService).saveSubmission(eq(SUBMISSION_ID), any());
    }

    @Test
    @DisplayName("2. GET /result → 200 + challengeId")
    void getSubmissionResult_success() throws Exception {
        SubmissionResultResponse result = SubmissionResultResponse.builder()
                .challengeId(100L)
                .submissionChallengeId(SUBMISSION_ID)
                .sectionDetails(List.of())
                .build();

        when(submissionQuestionService.getSubmissionResult(SUBMISSION_ID))
                .thenReturn(result);

        mockMvc.perform(get("/api/v1/submission/{submissionChallengeId}/result", SUBMISSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.challengeId").value(100L));
    }

    @Test
    @DisplayName("3. GET /draft → 200 + status")
    void getDraftSubmission_success() throws Exception {
        DraftSubmissionResponse result = DraftSubmissionResponse.builder()
                .challengeId(100L)
                .submissionChallengeId(SUBMISSION_ID)
                .status(SubmissionStatus.DRAFT)
                .sectionDetails(List.of())
                .build();

        when(submissionQuestionService.getDraftSubmission(SUBMISSION_ID))
                .thenReturn(result);

        mockMvc.perform(get("/api/v1/submission/{submissionChallengeId}/draft", SUBMISSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("4. GET /question/{id} → 200 + questionId")
    void getQuestionDetail_success() throws Exception {
        SubmissionResultResponse.QuestionResult result = SubmissionResultResponse.QuestionResult.builder()
                .questionId(300L)
                .submissionQuestionId(SUBMISSION_QUESTION_ID)
                .questionText("What is 2+2?")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .score(10.0)
                .build();

        when(submissionQuestionService.getQuestionDetail(SUBMISSION_QUESTION_ID))
                .thenReturn(result);

        mockMvc.perform(get("/api/v1/submission/question/{submissionQuestionId}", SUBMISSION_QUESTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionId").value(300L));
    }
}