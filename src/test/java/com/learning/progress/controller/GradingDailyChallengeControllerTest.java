package com.learning.progress.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
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
                .andExpect(jsonPath("$.data.gradingChallengeId").value(200))
                .andExpect(jsonPath("$.data.totalWeight").value(8.5))
                .andExpect(jsonPath("$.data.maxPossibleWeight").value(10.0))
                .andExpect(jsonPath("$.data.finalScore").value(8.5))
                .andExpect(jsonPath("$.data.penaltyApplied").value(0.0))
                .andExpect(jsonPath("$.data.rawScore").value(8.5))
                .andExpect(jsonPath("$.data.totalQuestions").value(5))
                .andExpect(jsonPath("$.data.correctAnswers").value(4))
                .andExpect(jsonPath("$.data.wrongAnswers").value(1))
                .andExpect(jsonPath("$.data.skipped").value(0))
                .andExpect(jsonPath("$.data.empty").value(0))
                .andExpect(jsonPath("$.data.teacherFeedback").value("Good job!"));

        verify(gradingService, times(1)).getChallengeGradingDetail(submissionId);
    }

    @Test
    @DisplayName("2. Get challenge grading detail - not found")
    void getChallengeGradingDetail_notFound() throws Exception {
        Long submissionId = 999L;
        when(gradingService.getChallengeGradingDetail(submissionId))
                .thenThrow(new ApiException(Const.GRADING.GRADING_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/grading/submission-challenges/{id}", submissionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.GRADING.GRADING_NOT_FOUND));

        verify(gradingService, times(1)).getChallengeGradingDetail(submissionId);
    }

    @Test
    @DisplayName("3. Get challenge grading detail - submission not found")
    void getChallengeGradingDetail_submissionNotFound() throws Exception {
        Long submissionId = 999L;
        when(gradingService.getChallengeGradingDetail(submissionId))
                .thenThrow(new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/grading/submission-challenges/{id}", submissionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.NOT_FOUND));

        verify(gradingService, times(1)).getChallengeGradingDetail(submissionId);
    }

    @Test
    @DisplayName("4. Get challenge grading detail - no access")
    void getChallengeGradingDetail_noAccess() throws Exception {
        Long submissionId = 100L;
        when(gradingService.getChallengeGradingDetail(submissionId))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/grading/submission-challenges/{id}", submissionId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(gradingService, times(1)).getChallengeGradingDetail(submissionId);
    }

    @Test
    @DisplayName("5. Get challenge grading detail - with penalty applied")
    void getChallengeGradingDetail_withPenalty() throws Exception {
        Long submissionId = 100L;
        GradingChallengeDetailResponse response = GradingChallengeDetailResponse.builder()
                .gradingChallengeId(200L)
                .totalWeight(9.0)
                .maxPossibleWeight(10.0)
                .finalScore(8.1)
                .penaltyApplied(0.1)
                .rawScore(9.0)
                .totalQuestions(5)
                .correctAnswers(5)
                .wrongAnswers(0)
                .skipped(0)
                .empty(0)
                .teacherFeedback("Late submission")
                .build();

        when(gradingService.getChallengeGradingDetail(submissionId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/grading/submission-challenges/{id}", submissionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.penaltyApplied").value(0.1))
                .andExpect(jsonPath("$.data.finalScore").value(8.1))
                .andExpect(jsonPath("$.data.rawScore").value(9.0));

        verify(gradingService, times(1)).getChallengeGradingDetail(submissionId);
    }

    @Test
    @DisplayName("6. Get challenge grading detail - no feedback")
    void getChallengeGradingDetail_noFeedback() throws Exception {
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
                .teacherFeedback(null)
                .build();

        when(gradingService.getChallengeGradingDetail(submissionId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/grading/submission-challenges/{id}", submissionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherFeedback").isEmpty());

        verify(gradingService, times(1)).getChallengeGradingDetail(submissionId);
    }

    @Test
    @DisplayName("7. Get challenge grading detail - all questions skipped")
    void getChallengeGradingDetail_allSkipped() throws Exception {
        Long submissionId = 100L;
        GradingChallengeDetailResponse response = GradingChallengeDetailResponse.builder()
                .gradingChallengeId(200L)
                .totalWeight(0.0)
                .maxPossibleWeight(10.0)
                .finalScore(0.0)
                .penaltyApplied(0.0)
                .rawScore(0.0)
                .totalQuestions(5)
                .correctAnswers(0)
                .wrongAnswers(0)
                .skipped(5)
                .empty(0)
                .teacherFeedback("No answers provided")
                .build();

        when(gradingService.getChallengeGradingDetail(submissionId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/grading/submission-challenges/{id}", submissionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skipped").value(5))
                .andExpect(jsonPath("$.data.correctAnswers").value(0));

        verify(gradingService, times(1)).getChallengeGradingDetail(submissionId);
    }

    // ========================================================================
    // 2. GET /submission-questions/{submissionQuestionId}
    // ========================================================================

    @Test
    @DisplayName("8. Get question grading detail - success")
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
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.gradingQuestionId").value(400))
                .andExpect(jsonPath("$.data.submissionQuestionId").value(300))
                .andExpect(jsonPath("$.data.receivedWeight").value(8.0))
                .andExpect(jsonPath("$.data.questionWeight").value(10.0))
                .andExpect(jsonPath("$.data.feedback.overallFeedback").value("Well written"))
                .andExpect(jsonPath("$.data.highlightComments[0].comment").value("Good vocabulary"))
                .andExpect(jsonPath("$.data.highlightComments[1].comment").value("Grammar error"))
                .andExpect(jsonPath("$.data.graderId").value(50))
                .andExpect(jsonPath("$.data.graderName").value("Teacher A"));

        verify(gradingService, times(1)).getQuestionGradingDetail(sqId);
    }

    @Test
    @DisplayName("9. Get question grading detail - not found")
    void getQuestionGradingDetail_notFound() throws Exception {
        Long sqId = 999L;
        when(gradingService.getQuestionGradingDetail(sqId))
                .thenThrow(new ApiException(Const.GRADING.GRADING_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/grading/submission-questions/{id}", sqId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.GRADING.GRADING_NOT_FOUND));

        verify(gradingService, times(1)).getQuestionGradingDetail(sqId);
    }

    @Test
    @DisplayName("10. Get question grading detail - no access")
    void getQuestionGradingDetail_noAccess() throws Exception {
        Long sqId = 300L;
        when(gradingService.getQuestionGradingDetail(sqId))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/grading/submission-questions/{id}", sqId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(gradingService, times(1)).getQuestionGradingDetail(sqId);
    }

    @Test
    @DisplayName("11. Get question grading detail - no feedback")
    void getQuestionGradingDetail_noFeedback() throws Exception {
        Long sqId = 300L;
        GradingQuestionDetailResponse response = GradingQuestionDetailResponse.builder()
                .gradingQuestionId(400L)
                .submissionQuestionId(sqId)
                .receivedWeight(8.0)
                .questionWeight(10.0)
                .feedback(null)
                .highlightComments(null)
                .graderId(50L)
                .graderName("Teacher A")
                .build();

        when(gradingService.getQuestionGradingDetail(sqId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/grading/submission-questions/{id}", sqId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback").isEmpty())
                .andExpect(jsonPath("$.data.highlightComments").isEmpty());

        verify(gradingService, times(1)).getQuestionGradingDetail(sqId);
    }

    @Test
    @DisplayName("12. Get question grading detail - zero weight")
    void getQuestionGradingDetail_zeroWeight() throws Exception {
        Long sqId = 300L;
        GradingQuestionDetailResponse response = GradingQuestionDetailResponse.builder()
                .gradingQuestionId(400L)
                .submissionQuestionId(sqId)
                .receivedWeight(0.0)
                .questionWeight(10.0)
                .feedback(new FeedbackContent("Incorrect", 0.0, null))
                .highlightComments(null)
                .graderId(50L)
                .graderName("Teacher A")
                .build();

        when(gradingService.getQuestionGradingDetail(sqId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/grading/submission-questions/{id}", sqId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receivedWeight").value(0.0));

        verify(gradingService, times(1)).getQuestionGradingDetail(sqId);
    }

    @Test
    @DisplayName("13. Get question grading detail - full weight")
    void getQuestionGradingDetail_fullWeight() throws Exception {
        Long sqId = 300L;
        GradingQuestionDetailResponse response = GradingQuestionDetailResponse.builder()
                .gradingQuestionId(400L)
                .submissionQuestionId(sqId)
                .receivedWeight(10.0)
                .questionWeight(10.0)
                .feedback(new FeedbackContent("Perfect!", 10.0, null))
                .highlightComments(null)
                .graderId(50L)
                .graderName("Teacher A")
                .build();

        when(gradingService.getQuestionGradingDetail(sqId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/grading/submission-questions/{id}", sqId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receivedWeight").value(10.0))
                .andExpect(jsonPath("$.data.questionWeight").value(10.0));

        verify(gradingService, times(1)).getQuestionGradingDetail(sqId);
    }

    // ========================================================================
    // 3. POST /submission-challenges/{submissionChallengeId} - Summary
    // ========================================================================

    @Test
    @DisplayName("14. Grade submission challenge - success")
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
    @DisplayName("15. Grade submission challenge - rawScore < 0 → 400")
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
    @DisplayName("16. Grade submission challenge - rawScore > 10 → 400")
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
    @DisplayName("17. Grade submission challenge - penalty < 0 → 400")
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
    @DisplayName("18. Grade submission challenge - penalty > 1 → 400")
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

    @Test
    @DisplayName("19. Grade submission challenge - rawScore null → 400")
    void gradeSubmissionChallenge_rawScore_null() throws Exception {
        Long submissionId = 100L;
        String json = """
                {
                  "penaltyApplied": 0.0,
                  "overallFeedback": "Good"
                }
                """;

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Raw score is required"));

        verifyNoInteractions(gradingService);
    }

    @Test
    @DisplayName("20. Grade submission challenge - penaltyApplied null → 200")
    void gradeSubmissionChallenge_penalty_null() throws Exception {
        Long submissionId = 100L;
        String json = """
                {
                  "rawScore": 8.0,
                  "overallFeedback": "Good"
                }
                """;

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(gradingService).gradeSubmissionChallenge(eq(submissionId), any(GradeSummaryRequest.class));
    }

    @Test
    @DisplayName("21. Grade submission challenge - submission not found → 404")
    void gradeSubmissionChallenge_submissionNotFound() throws Exception {
        Long submissionId = 999L;
        GradeSummaryRequest request = new GradeSummaryRequest(8.5, 0.0, "Good");

        doThrow(new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()))
                .when(gradingService).gradeSubmissionChallenge(eq(submissionId), any());

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.NOT_FOUND));

        verify(gradingService, times(1)).gradeSubmissionChallenge(eq(submissionId), any());
    }

    @Test
    @DisplayName("22. Grade submission challenge - no class access → 403")
    void gradeSubmissionChallenge_noAccess() throws Exception {
        Long submissionId = 100L;
        GradeSummaryRequest request = new GradeSummaryRequest(8.5, 0.0, "Good");

        doThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()))
                .when(gradingService).gradeSubmissionChallenge(eq(submissionId), any());

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(gradingService, times(1)).gradeSubmissionChallenge(eq(submissionId), any());
    }

    @Test
    @DisplayName("23. Grade submission challenge - with penalty")
    void gradeSubmissionChallenge_withPenalty() throws Exception {
        Long submissionId = 100L;
        GradeSummaryRequest request = new GradeSummaryRequest(9.0, 0.5, "Late submission");

        doNothing().when(gradingService).gradeSubmissionChallenge(eq(submissionId), any());

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionChallenge(eq(submissionId), any());
    }

    @Test
    @DisplayName("24. Grade submission challenge - zero score")
    void gradeSubmissionChallenge_zeroScore() throws Exception {
        Long submissionId = 100L;
        GradeSummaryRequest request = new GradeSummaryRequest(0.0, 0.0, "All incorrect");

        doNothing().when(gradingService).gradeSubmissionChallenge(eq(submissionId), any());

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionChallenge(eq(submissionId), any());
    }

    @Test
    @DisplayName("25. Grade submission challenge - perfect score")
    void gradeSubmissionChallenge_perfectScore() throws Exception {
        Long submissionId = 100L;
        GradeSummaryRequest request = new GradeSummaryRequest(10.0, 0.0, "Perfect!");

        doNothing().when(gradingService).gradeSubmissionChallenge(eq(submissionId), any());

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionChallenge(eq(submissionId), any());
    }

    @Test
    @DisplayName("26. Grade submission challenge - no feedback")
    void gradeSubmissionChallenge_noFeedback() throws Exception {
        Long submissionId = 100L;
        GradeSummaryRequest request = new GradeSummaryRequest(8.5, 0.0, null);

        doNothing().when(gradingService).gradeSubmissionChallenge(eq(submissionId), any());

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionChallenge(eq(submissionId), any());
    }

    @Test
    @DisplayName("27. Grade submission challenge - empty feedback")
    void gradeSubmissionChallenge_emptyFeedback() throws Exception {
        Long submissionId = 100L;
        GradeSummaryRequest request = new GradeSummaryRequest(8.5, 0.0, "");

        doNothing().when(gradingService).gradeSubmissionChallenge(eq(submissionId), any());

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionChallenge(eq(submissionId), any());
    }

    @Test
    @DisplayName("28. Grade submission challenge - malformed JSON → 400")
    void gradeSubmissionChallenge_malformedJson() throws Exception {
        Long submissionId = 100L;
        String malformedJson = "{ invalid json }";

        mockMvc.perform(post("/api/v1/grading/submission-challenges/{id}", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(gradingService);
    }

    // ========================================================================
    // 4. POST /submission-questions/{submissionQuestionId} - Per question
    // ========================================================================

    @Test
    @DisplayName("29. Grade submission question - success")
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
    @DisplayName("30. Grade question - receivedWeight null → 400")
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
    @DisplayName("31. Grade question - receivedWeight negative → 400")
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
    @DisplayName("32. Grade question - submissionQuestion not found → 404")
    void gradeSubmissionQuestion_notFound() throws Exception {
        Long sqId = 999L;
        GradeQuestionRequest request = new GradeQuestionRequest(8.0, null, null);

        doThrow(new ApiException(Const.GRADING.SUBMISSION_QUESTION_NOT_FOUND, HttpStatus.NOT_FOUND.value()))
                .when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.GRADING.SUBMISSION_QUESTION_NOT_FOUND));

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("33. Grade question - receivedWeight exceeds max → 400")
    void gradeSubmissionQuestion_exceedsMax() throws Exception {
        Long sqId = 300L;
        GradeQuestionRequest request = new GradeQuestionRequest(15.0, null, null);

        doThrow(new ApiException(Const.GRADING.RECEIVED_WEIGHT_EXCEEDS_MAX, HttpStatus.BAD_REQUEST.value()))
                .when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.GRADING.RECEIVED_WEIGHT_EXCEEDS_MAX));

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("34. Grade question - invalid challenge type → 400")
    void gradeSubmissionQuestion_invalidChallengeType() throws Exception {
        Long sqId = 300L;
        GradeQuestionRequest request = new GradeQuestionRequest(8.0, null, null);

        doThrow(new ApiException(Const.GRADING.MANUAL_GRADING_ONLY_WR_SP, HttpStatus.BAD_REQUEST.value()))
                .when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.GRADING.MANUAL_GRADING_ONLY_WR_SP));

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("35. Grade question - no class access → 403")
    void gradeSubmissionQuestion_noAccess() throws Exception {
        Long sqId = 300L;
        GradeQuestionRequest request = new GradeQuestionRequest(8.0, null, null);

        doThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()))
                .when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("36. Grade question - zero weight")
    void gradeSubmissionQuestion_zeroWeight() throws Exception {
        Long sqId = 300L;
        GradeQuestionRequest request = new GradeQuestionRequest(0.0, null, null);

        doNothing().when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("37. Grade question - full weight")
    void gradeSubmissionQuestion_fullWeight() throws Exception {
        Long sqId = 300L;
        GradeQuestionRequest request = new GradeQuestionRequest(10.0, null, null);

        doNothing().when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("38. Grade question - with feedback only")
    void gradeSubmissionQuestion_feedbackOnly() throws Exception {
        Long sqId = 300L;
        FeedbackContent feedback = new FeedbackContent("Good work", 8.0, null);
        GradeQuestionRequest request = new GradeQuestionRequest(8.0, feedback, null);

        doNothing().when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("39. Grade question - with highlights only")
    void gradeSubmissionQuestion_highlightsOnly() throws Exception {
        Long sqId = 300L;
        List<HighlightComment> highlights = List.of(
                new HighlightComment(5, 15, "Good point", "h1", "2025-01-01T12:00:00Z")
        );
        GradeQuestionRequest request = new GradeQuestionRequest(8.0, null, highlights);

        doNothing().when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("40. Grade question - with multiple highlights")
    void gradeSubmissionQuestion_multipleHighlights() throws Exception {
        Long sqId = 300L;
        List<HighlightComment> highlights = Arrays.asList(
                new HighlightComment(5, 15, "Good intro", "h1", "2025-01-01T12:00:00Z"),
                new HighlightComment(20, 30, "Clear argument", "h2", "2025-01-01T12:01:00Z"),
                new HighlightComment(40, 50, "Strong conclusion", "h3", "2025-01-01T12:02:00Z")
        );
        GradeQuestionRequest request = new GradeQuestionRequest(9.0, null, highlights);

        doNothing().when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("41. Grade question - with feedback and highlights")
    void gradeSubmissionQuestion_fullGrading() throws Exception {
        Long sqId = 300L;
        FeedbackContent feedback = new FeedbackContent("Excellent work overall", 9.5, null);
        List<HighlightComment> highlights = Arrays.asList(
                new HighlightComment(10, 20, "Strong vocabulary", "h1", "2025-01-01T12:00:00Z"),
                new HighlightComment(30, 40, "Minor grammar issue", "h2", "2025-01-01T12:01:00Z")
        );
        GradeQuestionRequest request = new GradeQuestionRequest(9.5, feedback, highlights);

        doNothing().when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("42. Grade question - empty feedback")
    void gradeSubmissionQuestion_emptyFeedback() throws Exception {
        Long sqId = 300L;
        FeedbackContent feedback = new FeedbackContent("", null, null);
        GradeQuestionRequest request = new GradeQuestionRequest(8.0, feedback, null);

        doNothing().when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("43. Grade question - empty highlights list")
    void gradeSubmissionQuestion_emptyHighlightsList() throws Exception {
        Long sqId = 300L;
        GradeQuestionRequest request = new GradeQuestionRequest(8.0, null, List.of());

        doNothing().when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("44. Grade question - extra field ignored")
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
    @DisplayName("45. Grade question - malformed JSON → 400")
    void gradeSubmissionQuestion_malformedJson() throws Exception {
        Long sqId = 300L;
        String malformedJson = "{ invalid json }";

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(gradingService);
    }

    @Test
    @DisplayName("46. Grade question - partial credit")
    void gradeSubmissionQuestion_partialCredit() throws Exception {
        Long sqId = 300L;
        FeedbackContent feedback = new FeedbackContent("Partial answer, needs improvement", 5.0, null);
        GradeQuestionRequest request = new GradeQuestionRequest(5.0, feedback, null);

        doNothing().when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("47. Grade question - decimal weight")
    void gradeSubmissionQuestion_decimalWeight() throws Exception {
        Long sqId = 300L;
        GradeQuestionRequest request = new GradeQuestionRequest(8.75, null, null);

        doNothing().when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("48. Grade question - minimal weight")
    void gradeSubmissionQuestion_minimalWeight() throws Exception {
        Long sqId = 300L;
        GradeQuestionRequest request = new GradeQuestionRequest(0.01, null, null);

        doNothing().when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("49. Grade question - service throws exception")
    void gradeSubmissionQuestion_service_throws() throws Exception {
        Long sqId = 300L;
        GradeQuestionRequest request = new GradeQuestionRequest(8.0, null, null);

        doThrow(new ApiException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR.value()))
                .when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }

    @Test
    @DisplayName("50. Grade question - long feedback text")
    void gradeSubmissionQuestion_longFeedback() throws Exception {
        Long sqId = 300L;
        String longFeedback = "This is a very long feedback that contains multiple sentences. " +
                "The student has demonstrated good understanding of the topic. " +
                "However, there are areas that need improvement, particularly in grammar and structure. " +
                "Overall, this is a solid effort and shows progress.";
        FeedbackContent feedback = new FeedbackContent(longFeedback, 7.5, null);
        GradeQuestionRequest request = new GradeQuestionRequest(7.5, feedback, null);

        doNothing().when(gradingService).gradeSubmissionQuestion(eq(sqId), any());

        mockMvc.perform(post("/api/v1/grading/submission-questions/{id}", sqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(gradingService, times(1)).gradeSubmissionQuestion(eq(sqId), any());
    }
}