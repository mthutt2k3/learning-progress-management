package com.learning.progress.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.grading.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.exception.GlobalExceptionHandler;
import com.learning.progress.service.GradingDailyChallengeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class GradingDailyChallengeControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private GradingDailyChallengeService gradingService;

    @InjectMocks
    private GradingDailyChallengeController controller;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ========================================================================
    // 1. GET /submission-challenges/{submissionChallengeId}
    // ========================================================================

    @Test
    @DisplayName("1. Get challenge grading detail - success")
    void getChallengeGradingDetail_success() throws Exception {
        Long submissionId = 100L;
        GradingChallengeDetailResponse response = GradingChallengeDetailResponse.builder()
                .gradingChallengeId(200L)
                .totalWeight(8.5)
                .maxPossibleWeight(10.0)
                .finalScore(8.5)
                .penaltyApplied(0.0)
                .rawScore(8.5)
                .totalQuestions(5)
                .correctAnswers(4)
                .wrongAnswers(1)
                .skipped(0)
                .empty(0)
                .teacherFeedback("Good job!")
                .build();

        when(gradingService.getChallengeGradingDetail(submissionId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/grading/submission-challenges/{id}", submissionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL))
                .andExpect(jsonPath("$.data.totalWeight").value(8.5))
                .andExpect(jsonPath("$.data.teacherFeedback").value("Good job!"));

        verify(gradingService, times(1)).getChallengeGradingDetail(submissionId);
    }

    @Test
    @DisplayName("2. Get challenge grading detail - not found")
    void getChallengeGradingDetail_notFound() throws Exception {
        Long submissionId = 999L;
        when(gradingService.getChallengeGradingDetail(submissionId))
                .thenThrow(new ApiException("Grading not found", HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/grading/submission-challenges/{id}", submissionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Grading not found"));

        verify(gradingService, times(1)).getChallengeGradingDetail(submissionId);
    }

    // ========================================================================
    // 2. GET /submission-questions/{submissionQuestionId}
    // ========================================================================

    @Test
    @DisplayName("3. Get question grading detail - success")
    void getQuestionGradingDetail_success() throws Exception {
        Long sqId = 300L;
        FeedbackContent feedback = new FeedbackContent("Well written", 8.0, null);
        List<HighlightComment> highlights = Arrays.asList(
                new HighlightComment(10, 20, "Good vocabulary", "hl1", "2025-01-01T10:00:00Z"),
                new HighlightComment(30, 40, "Grammar error", "hl2", "2025-01-01T10:01:00Z")
        );

        GradingQuestionDetailResponse response = GradingQuestionDetailResponse.builder()
                .gradingQuestionId(400L)
                .submissionQuestionId(sqId)
                .receivedWeight(8.0)
                .questionWeight(10.0)
                .feedback(feedback)
                .highlightComments(highlights)
                .graderId(50L)
                .graderName("Teacher A")
                .build();

        when(gradingService.getQuestionGradingDetail(sqId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/grading/submission-questions/{id}", sqId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback.overallFeedback").value("Well written"))
                .andExpect(jsonPath("$.data.highlightComments[0].comment").value("Good vocabulary"))
                .andExpect(jsonPath("$.data.graderName").value("Teacher A"));

        verify(gradingService, times(1)).getQuestionGradingDetail(sqId);
    }

    @Test
    @DisplayName("4. Get question grading detail - not found")
    void getQuestionGradingDetail_notFound() throws Exception {
        Long sqId = 999L;
        when(gradingService.getQuestionGradingDetail(sqId))
                .thenThrow(new ApiException("Grading not found", HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/grading/submission-questions/{id}", sqId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Grading not found"));

        verify(gradingService, times(1)).getQuestionGradingDetail(sqId);
    }

    // ========================================================================
    // 3. POST /submission-challenges/{submissionChallengeId} - Summary
    // ========================================================================

    @Test
    @DisplayName("5. Grade submission challenge - success")
    void gradeSubmissionChallenge_success() throws Exception {
        Long submissionId = 100L;
        GradeSummaryRequest request = new GradeSummaryRequest(8.5, 0.0, "Great effort!");

        doNothing().when(gradingService).gradeSubmissionChallenge(eq(submissionId), any(GradeSummaryRequest.class));

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));

        verify(gradingService, times(1)).gradeSubmissionChallenge(eq(submissionId), any());
    }

    @Test
    @DisplayName("6. Grade submission challenge - rawScore < 0 → 400")
    void gradeSubmissionChallenge_rawScore_negative() throws Exception {
        Long submissionId = 100L;
        GradeSummaryRequest request = new GradeSummaryRequest(-1.0, 0.0, "Invalid");

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Raw score must be 0-10"));

        verifyNoInteractions(gradingService);
    }

    @Test
    @DisplayName("7. Grade submission challenge - rawScore > 10 → 400")
    void gradeSubmissionChallenge_rawScore_tooHigh() throws Exception {
        Long submissionId = 100L;
        GradeSummaryRequest request = new GradeSummaryRequest(11.0, 0.0, "Too high");

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Raw score must be 0-10"));

        verifyNoInteractions(gradingService);
    }

    @Test
    @DisplayName("8. Grade submission challenge - penalty < 0 → 400")
    void gradeSubmissionChallenge_penalty_negative() throws Exception {
        Long submissionId = 100L;
        GradeSummaryRequest request = new GradeSummaryRequest(8.0, -0.1, "Invalid penalty");

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Penalty must be 0.0-1.0"));

        verifyNoInteractions(gradingService);
    }

    @Test
    @DisplayName("9. Grade submission challenge - penalty > 1 → 400")
    void gradeSubmissionChallenge_penalty_tooHigh() throws Exception {
        Long submissionId = 100L;
        GradeSummaryRequest request = new GradeSummaryRequest(8.0, 1.1, "Too much penalty");

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Penalty must be 0.0-1.0"));

        verifyNoInteractions(gradingService);
    }

    // ========================================================================
    // 4. POST /submission-questions/{submissionQuestionId} - Per question
    // ========================================================================

    @Test
    @DisplayName("10. Grade submission question - success")
    void gradeSubmissionQuestion_success() throws Exception {
        Long sqId = 300L;
        FeedbackContent feedback = new FeedbackContent("Good structure", 8.5, null);
        List<HighlightComment> highlights = List.of(
                new HighlightComment(5, 15, "Clear thesis", "h1", "2025-01-01T12:00:00Z")
        );
        GradeQuestionRequest request = new GradeQuestionRequest(8.5, feedback, highlights);

        doNothing().when(gradingService).gradeSubmissionQuestion(eq(sqId), any(GradeQuestionRequest.class));

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("11. Grade question - receivedWeight null → 400")
    void gradeSubmissionQuestion_receivedWeight_null() throws Exception {
        Long sqId = 300L;
        GradeQuestionRequest request = new GradeQuestionRequest(null, null, null);

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("receivedWeight is required"));

        verifyNoInteractions(gradingService);
    }

    @Test
    @DisplayName("12. Grade question - receivedWeight negative → 400")
    void gradeSubmissionQuestion_receivedWeight_negative() throws Exception {
        Long sqId = 300L;
        GradeQuestionRequest request = new GradeQuestionRequest(-1.0, null, null);

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("receivedWeight must be non-negative"));

        verifyNoInteractions(gradingService);
    }

    @Test
    @DisplayName("15. Grade question - extra field ignored")
    void gradeSubmissionQuestion_extraField_ignored() throws Exception {
        Long sqId = 300L;
        String json = """
                {
                  "receivedWeight": 8.0,
                  "feedback": { "overallFeedback": "Good" },
                  "highlightComments": [],
                  "extraField": "should be ignored"
                }
                """;

        doNothing().when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("16. Grade question - service throws exception → 400/404")
    void gradeSubmissionQuestion_service_throws() throws Exception {
        Long sqId = 300L;
        GradeQuestionRequest request = new GradeQuestionRequest(8.0, null, null);

        doThrow(new ApiException("Received weight exceeds max", HttpStatus.BAD_REQUEST.value()))
                .when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Received weight exceeds max"));

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }
}