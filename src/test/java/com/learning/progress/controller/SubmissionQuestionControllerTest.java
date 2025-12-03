package com.learning.progress.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
import com.learning.progress.common.QuestionType;
import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.dto.submission.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.exception.GlobalExceptionHandler;
import com.learning.progress.service.SubmissionQuestionService;
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

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SubmissionQuestionControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private SubmissionQuestionService submissionQuestionService;

    @InjectMocks
    private SubmissionQuestionController controller;

    private final Long SUBMISSION_ID = 200L;
    private final Long SUBMISSION_QUESTION_ID = 400L;
    private final Long QUESTION_ID = 300L;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ====================== POST /{submissionChallengeId} ======================

    @Test
    @DisplayName("1. Save submission - success as draft → 200")
    void saveSubmission_successDraft_200() throws Exception {
        SaveSubmissionRequest request = SaveSubmissionRequest.builder()
                .saveAsDraft(true)
                .questionAnswers(List.of())
                .build();

        doNothing().when(submissionQuestionService).saveSubmission(eq(SUBMISSION_ID), any());

        mockMvc.perform(post("/api/v1/submission/{submissionChallengeId}", SUBMISSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));

        verify(submissionQuestionService, times(1)).saveSubmission(eq(SUBMISSION_ID), any());
    }

    @Test
    @DisplayName("2. Save submission - success as submit → 200")
    void saveSubmission_successSubmit_200() throws Exception {
        SaveSubmissionRequest request = SaveSubmissionRequest.builder()
                .saveAsDraft(false)
                .questionAnswers(List.of(SaveSubmissionRequest.QuestionAnswer.builder()
                        .questionId(QUESTION_ID)
                        .content(new AnswerContent())
                        .build()))
                .build();

        doNothing().when(submissionQuestionService).saveSubmission(eq(SUBMISSION_ID), any());

        mockMvc.perform(post("/api/v1/submission/{submissionChallengeId}", SUBMISSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(submissionQuestionService, times(1)).saveSubmission(eq(SUBMISSION_ID), any());
    }

//    @Test
//    @DisplayName("3. Save submission - already completed → 400")
//    void saveSubmission_alreadyCompleted_400() throws Exception {
//        SaveSubmissionRequest request = SaveSubmissionRequest.builder()
//                .saveAsDraft(true)
//                .build();
//
//        doThrow(new ApiException(Const.SUBMISSION.ALREADY_COMPLETED, HttpStatus.BAD_REQUEST.value()))
//                .when(submissionQuestionService).saveSubmission(eq(SUBMISSION_ID), any());
//
//        mockMvc.perform(post("/api/v1/submission/{submissionChallengeId}", SUBMISSION_ID)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.QUESTION_ANSWERS_REQUIRED));
//
//        verify(submissionQuestionService, times(1)).saveSubmission(eq(SUBMISSION_ID), any());
//    }

//    @Test
//    @DisplayName("4. Save submission - missed → 400")
//    void saveSubmission_missed_400() throws Exception {
//        SaveSubmissionRequest request = SaveSubmissionRequest.builder()
//                .saveAsDraft(true)
//                .build();
//
//        doThrow(new ApiException(Const.SUBMISSION.SUBMISSION_MISSED, HttpStatus.BAD_REQUEST.value()))
//                .when(submissionQuestionService).saveSubmission(eq(SUBMISSION_ID), any());
//
//        mockMvc.perform(post("/api/v1/submission/{submissionChallengeId}", SUBMISSION_ID)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.QUESTION_ANSWERS_REQUIRED));
//
//        verify(submissionQuestionService, times(1)).saveSubmission(eq(SUBMISSION_ID), any());
//    }

    @Test
    @DisplayName("5. Save submission - invalid questions → 400")
    void saveSubmission_invalidQuestions_400() throws Exception {
        SaveSubmissionRequest request = SaveSubmissionRequest.builder()
                .saveAsDraft(true)
                .questionAnswers(List.of(SaveSubmissionRequest.QuestionAnswer.builder()
                        .questionId(999L)
                        .build()))
                .build();

        doThrow(new ApiException(String.format(Const.QUESTION.QUESTION_NOT_FOUND_WITH_ID, 999L), HttpStatus.NOT_FOUND.value()))
                .when(submissionQuestionService).saveSubmission(eq(SUBMISSION_ID), any());

        mockMvc.perform(post("/api/v1/submission/{submissionChallengeId}", SUBMISSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        verify(submissionQuestionService, times(1)).saveSubmission(eq(SUBMISSION_ID), any());
    }

//    @Test
//    @DisplayName("6. Save submission - submission not found → 404")
//    void saveSubmission_notFound_404() throws Exception {
//        SaveSubmissionRequest request = SaveSubmissionRequest.builder()
//                .saveAsDraft(true)
//                .build();
//
//        doThrow(new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()))
//                .when(submissionQuestionService).saveSubmission(eq(999L), any());
//
//        mockMvc.perform(post("/api/v1/submission/999")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.QUESTION_ANSWERS_REQUIRED));
//
//        verify(submissionQuestionService, times(1)).saveSubmission(eq(999L), any());
//    }

//    @Test
//    @DisplayName("7. Save submission - no access → 403")
//    void saveSubmission_noAccess_403() throws Exception {
//        SaveSubmissionRequest request = SaveSubmissionRequest.builder()
//                .saveAsDraft(true)
//                .build();
//
//        doThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()))
//                .when(submissionQuestionService).saveSubmission(eq(SUBMISSION_ID), any());
//
//        mockMvc.perform(post("/api/v1/submission/{submissionChallengeId}", SUBMISSION_ID)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.QUESTION_ANSWERS_REQUIRED));
//
//        verify(submissionQuestionService, times(1)).saveSubmission(eq(SUBMISSION_ID), any());
//    }

//    @Test
//    @DisplayName("8. Save submission - class not active → 400")
//    void saveSubmission_classNotActive_400() throws Exception {
//        SaveSubmissionRequest request = SaveSubmissionRequest.builder()
//                .saveAsDraft(true)
//                .build();
//
//        doThrow(new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.BAD_REQUEST.value()))
//                .when(submissionQuestionService).saveSubmission(eq(SUBMISSION_ID), any());
//
//        mockMvc.perform(post("/api/v1/submission/{submissionChallengeId}", SUBMISSION_ID)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.QUESTION_ANSWERS_REQUIRED));
//
//        verify(submissionQuestionService, times(1)).saveSubmission(eq(SUBMISSION_ID), any());
//    }

    @Test
    @DisplayName("9. Save submission - malformed JSON → 400")
    void saveSubmission_malformedJson_400() throws Exception {
        String malformedJson = "{ invalid json }";

        mockMvc.perform(post("/api/v1/submission/{submissionChallengeId}", SUBMISSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionQuestionService);
    }

    @Test
    @DisplayName("10. Save submission - invalid ID → 400")
    void saveSubmission_invalidId_400() throws Exception {
        mockMvc.perform(post("/api/v1/submission/abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionQuestionService);
    }

    // ====================== GET /{submissionChallengeId}/result ======================

    @Test
    @DisplayName("11. Get submission result - success → 200")
    void getSubmissionResult_success_200() throws Exception {
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
                .andExpect(jsonPath("$.data.challengeId").value(100L))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));

        verify(submissionQuestionService, times(1)).getSubmissionResult(SUBMISSION_ID);
    }

    @Test
    @DisplayName("12. Get submission result - no sections → 404")
    void getSubmissionResult_noSections_404() throws Exception {
        when(submissionQuestionService.getSubmissionResult(SUBMISSION_ID))
                .thenThrow(new ApiException(Const.SUBMISSION.NO_SECTIONS_FOR_CHALLENGE, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/submission/{submissionChallengeId}/result", SUBMISSION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.NO_SECTIONS_FOR_CHALLENGE));

        verify(submissionQuestionService, times(1)).getSubmissionResult(SUBMISSION_ID);
    }

    @Test
    @DisplayName("13. Get submission result - not found → 404")
    void getSubmissionResult_notFound_404() throws Exception {
        when(submissionQuestionService.getSubmissionResult(999L))
                .thenThrow(new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/submission/999/result"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.NOT_FOUND));

        verify(submissionQuestionService, times(1)).getSubmissionResult(999L);
    }

    @Test
    @DisplayName("14. Get submission result - no access → 403")
    void getSubmissionResult_noAccess_403() throws Exception {
        when(submissionQuestionService.getSubmissionResult(SUBMISSION_ID))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/submission/{submissionChallengeId}/result", SUBMISSION_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(submissionQuestionService, times(1)).getSubmissionResult(SUBMISSION_ID);
    }

    @Test
    @DisplayName("15. Get submission result - invalid ID → 400")
    void getSubmissionResult_invalidId_400() throws Exception {
        mockMvc.perform(get("/api/v1/submission/abc/result"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionQuestionService);
    }

    // ====================== GET /{submissionChallengeId}/draft ======================

    @Test
    @DisplayName("16. Get draft submission - success → 200")
    void getDraftSubmission_success_200() throws Exception {
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
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));

        verify(submissionQuestionService, times(1)).getDraftSubmission(SUBMISSION_ID);
    }

    @Test
    @DisplayName("17. Get draft submission - not found → 404")
    void getDraftSubmission_notFound_404() throws Exception {
        when(submissionQuestionService.getDraftSubmission(999L))
                .thenThrow(new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/submission/999/draft"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.NOT_FOUND));

        verify(submissionQuestionService, times(1)).getDraftSubmission(999L);
    }

    @Test
    @DisplayName("18. Get draft submission - no access → 403")
    void getDraftSubmission_noAccess_403() throws Exception {
        when(submissionQuestionService.getDraftSubmission(SUBMISSION_ID))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/submission/{submissionChallengeId}/draft", SUBMISSION_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(submissionQuestionService, times(1)).getDraftSubmission(SUBMISSION_ID);
    }

    @Test
    @DisplayName("19. Get draft submission - invalid ID → 400")
    void getDraftSubmission_invalidId_400() throws Exception {
        mockMvc.perform(get("/api/v1/submission/abc/draft"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionQuestionService);
    }

    // ====================== GET /question/{submissionQuestionId} ======================

    @Test
    @DisplayName("20. Get question detail - success → 200")
    void getQuestionDetail_success_200() throws Exception {
        SubmissionResultResponse.QuestionResult result = SubmissionResultResponse.QuestionResult.builder()
                .questionId(QUESTION_ID)
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
                .andExpect(jsonPath("$.data.questionId").value(QUESTION_ID.intValue()))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));

        verify(submissionQuestionService, times(1)).getQuestionDetail(SUBMISSION_QUESTION_ID);
    }

    @Test
    @DisplayName("21. Get question detail - not found → 404")
    void getQuestionDetail_notFound_404() throws Exception {
        when(submissionQuestionService.getQuestionDetail(999L))
                .thenThrow(new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/submission/question/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.NOT_FOUND));

        verify(submissionQuestionService, times(1)).getQuestionDetail(999L);
    }

    @Test
    @DisplayName("22. Get question detail - deleted → 404")
    void getQuestionDetail_deleted_404() throws Exception {
        when(submissionQuestionService.getQuestionDetail(SUBMISSION_QUESTION_ID))
                .thenThrow(new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/submission/question/{submissionQuestionId}", SUBMISSION_QUESTION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.NOT_FOUND));

        verify(submissionQuestionService, times(1)).getQuestionDetail(SUBMISSION_QUESTION_ID);
    }

    @Test
    @DisplayName("23. Get question detail - no access → 403")
    void getQuestionDetail_noAccess_403() throws Exception {
        when(submissionQuestionService.getQuestionDetail(SUBMISSION_QUESTION_ID))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/submission/question/{submissionQuestionId}", SUBMISSION_QUESTION_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(submissionQuestionService, times(1)).getQuestionDetail(SUBMISSION_QUESTION_ID);
    }

    @Test
    @DisplayName("24. Get question detail - invalid ID → 400")
    void getQuestionDetail_invalidId_400() throws Exception {
        mockMvc.perform(get("/api/v1/submission/question/abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionQuestionService);
    }
}