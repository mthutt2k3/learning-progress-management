package com.learning.progress.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.StudentChallengeListDTO;
import com.learning.progress.dto.submission.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.exception.GlobalExceptionHandler;
import com.learning.progress.service.SubmissionChallengeService;
import com.learning.progress.service.SubmissionLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SubmissionChallengeControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock private SubmissionChallengeService submissionChallengeService;
    @Mock private SubmissionLogService submissionLogService;

    @InjectMocks private SubmissionChallengeController controller;

    private final Long CHALLENGE_ID = 100L;
    private final Long SUBMISSION_ID = 200L;
    private final Long CLASS_ID = 10L;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ====================== GET /class/{classId} ======================

    @Test
    @DisplayName("1. Student - List challenges → 200")
    void getAllChallengesForStudent_success() throws Exception {
        StudentChallengeListDTO dto = StudentChallengeListDTO.builder()
                .classLessonId(50L)
                .classLessonName("Lesson 1")
                .challenges(List.of(
                        StudentChallengeListDTO.StudentChallengeDTO.builder()
                                .studentSubmission(StudentSubmissionDTO.builder()
                                        .submissionId(SUBMISSION_ID)
                                        .submissionStatus(SubmissionStatus.DRAFT)
                                        .build())
                                .build()
                ))
                .build();

        DataResponse<List<StudentChallengeListDTO>> response = DataResponse.success(List.of(dto), Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .page(0).size(10).totalElements(1L).totalPages(1);

        when(submissionChallengeService.getAllChallengesForStudent(eq(CLASS_ID), eq(0), eq(10), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/challenge-submissions/class/{classId}", CLASS_ID)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].challenges[0].studentSubmission.submissionId").value(SUBMISSION_ID.intValue()));

        verify(submissionChallengeService, times(1)).getAllChallengesForStudent(anyLong(), anyInt(), anyInt(), isNull());
    }

    @Test
    @DisplayName("1. Student - Invalid page → 400")
    void getAllChallengesForStudent_invalidPage() throws Exception {
        when(submissionChallengeService.getAllChallengesForStudent(eq(CLASS_ID), eq(-1), eq(10), isNull()))
                .thenThrow(new ApiException("Page must be >= 0", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/class/{classId}", CLASS_ID)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page must be >= 0"));

        verify(submissionChallengeService, times(1)).getAllChallengesForStudent(eq(CLASS_ID), eq(-1), eq(10), isNull());
    }

    // ====================== GET /challenge/{challengeId}/submissions ======================

    @Test
    @DisplayName("2. Teacher - List submissions → 200")
    void getSubmissionsByChallenge_success() throws Exception {
        StudentSubmissionDTO dto = StudentSubmissionDTO.builder()
                .submissionId(SUBMISSION_ID)
                .studentName("Nguyen Van A")
                .submissionStatus(SubmissionStatus.SUBMITTED)
                .finalScore(85.0)
                .build();

        DataResponse<List<StudentSubmissionDTO>> response = DataResponse.success(List.of(dto), Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL);

        when(submissionChallengeService.getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(0), eq(10), isNull(), eq("createdAt"), eq("desc")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/challenge-submissions/challenge/{challengeId}/submissions", CHALLENGE_ID)
                        .param("sortBy", "createdAt")
                        .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].studentName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data[0].finalScore").value(85.0));

        verify(submissionChallengeService, times(1)).getSubmissionsByChallenge(anyLong(), anyInt(), anyInt(), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("2. Teacher - Invalid sortBy → 400")
    void getSubmissionsByChallenge_invalidSort() throws Exception {
        when(submissionChallengeService.getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(0), eq(10), isNull(), eq("invalid"), eq("asc")))
                .thenThrow(new ApiException("Invalid sort field", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/challenge/{challengeId}/submissions", CHALLENGE_ID)
                        .param("sortBy", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid sort field"));

        verify(submissionChallengeService, times(1)).getSubmissionsByChallenge(anyLong(), anyInt(), anyInt(), isNull(), eq("invalid"), eq("asc"));
    }

    // ====================== POST /submission/{submissionId}/start ======================

    @Test
    @DisplayName("3. Student starts submission → 200")
    void startSubmission_success() throws Exception {
        doNothing().when(submissionChallengeService).startSubmission(SUBMISSION_ID);

        mockMvc.perform(post("/api/v1/challenge-submissions/submission/{submissionId}/start", SUBMISSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));

        verify(submissionChallengeService, times(1)).startSubmission(SUBMISSION_ID);
    }

    @Test
    @DisplayName("3. Start submission - not found → 404")
    void startSubmission_notFound() throws Exception {
        doThrow(new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()))
                .when(submissionChallengeService).startSubmission(SUBMISSION_ID);

        mockMvc.perform(post("/api/v1/challenge-submissions/submission/{submissionId}/start", SUBMISSION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.NOT_FOUND));

        verify(submissionChallengeService, times(1)).startSubmission(SUBMISSION_ID);
    }

    @Test
    @DisplayName("3. Start submission - not owner → 403")
    void startSubmission_notOwner() throws Exception {
        doThrow(new ApiException(Const.SUBMISSION.FORBIDDEN_NOT_OWNER, HttpStatus.FORBIDDEN.value()))
                .when(submissionChallengeService).startSubmission(SUBMISSION_ID);

        mockMvc.perform(post("/api/v1/challenge-submissions/submission/{submissionId}/start", SUBMISSION_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.FORBIDDEN_NOT_OWNER));
    }

    // ====================== GET /{submissionChallengeId}/info ======================

    @Test
    @DisplayName("4. Get submission info → 200")
    void getSubmissionInfo_success() throws Exception {
        StudentSubmissionDTO dto = StudentSubmissionDTO.builder()
                .submissionId(SUBMISSION_ID)
                .submissionStatus(SubmissionStatus.DRAFT)
                .actualStartAt(OffsetDateTime.now().minusMinutes(10))
                .finalScore(75.0)
                .build();

        when(submissionChallengeService.getSubmissionInfo(SUBMISSION_ID)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/challenge-submissions/{submissionChallengeId}/info", SUBMISSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(SUBMISSION_ID.intValue()))
                .andExpect(jsonPath("$.data.finalScore").value(75.0))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));

        verify(submissionChallengeService, times(1)).getSubmissionInfo(SUBMISSION_ID);
    }

    @Test
    @DisplayName("6. Get submission info - invalid ID → 400")
    void getSubmissionInfo_invalidId() throws Exception {
        mockMvc.perform(get("/api/v1/challenge-submissions/{submissionChallengeId}/info", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionChallengeService);
    }
}