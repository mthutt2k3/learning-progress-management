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

    @Mock
    private SubmissionChallengeService submissionChallengeService;

    @Mock
    private SubmissionLogService submissionLogService;

    @InjectMocks
    private SubmissionChallengeController controller;

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
    @DisplayName("2. Student - List challenges with text filter → 200")
    void getAllChallengesForStudent_withText_success() throws Exception {

        List<StudentChallengeListDTO> emptyList = List.of();

        DataResponse<List<StudentChallengeListDTO>> response = DataResponse.success(emptyList, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .page(0).size(10).totalElements(0L).totalPages(0);

        when(submissionChallengeService.getAllChallengesForStudent(eq(CLASS_ID), eq(0), eq(10), eq("Math")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/challenge-submissions/class/{classId}", CLASS_ID)
                        .param("text", "Math"))
                .andExpect(status().isOk());

        verify(submissionChallengeService, times(1)).getAllChallengesForStudent(eq(CLASS_ID), eq(0), eq(10), eq("Math"));
    }

    @Test
    @DisplayName("3. Student - Invalid page → 400")
    void getAllChallengesForStudent_invalidPage_400() throws Exception {
        when(submissionChallengeService.getAllChallengesForStudent(eq(CLASS_ID), eq(-1), eq(10), isNull()))
                .thenThrow(new ApiException("Page must be >= 0", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/class/{classId}", CLASS_ID)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page must be >= 0"));

        verify(submissionChallengeService, times(1)).getAllChallengesForStudent(eq(CLASS_ID), eq(-1), eq(10), isNull());
    }

    @Test
    @DisplayName("4. Student - Invalid size → 400")
    void getAllChallengesForStudent_invalidSize_400() throws Exception {
        when(submissionChallengeService.getAllChallengesForStudent(eq(CLASS_ID), eq(0), eq(0), isNull()))
                .thenThrow(new ApiException("Size must be > 0", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/class/{classId}", CLASS_ID)
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Size must be > 0"));

        verify(submissionChallengeService, times(1)).getAllChallengesForStudent(eq(CLASS_ID), eq(0), eq(0), isNull());
    }

    @Test
    @DisplayName("5. Student - Class not found → 404")
    void getAllChallengesForStudent_classNotFound_404() throws Exception {
        when(submissionChallengeService.getAllChallengesForStudent(eq(999L), eq(0), eq(10), isNull()))
                .thenThrow(new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/class/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.CLASS.NOT_FOUND));

        verify(submissionChallengeService, times(1)).getAllChallengesForStudent(eq(999L), anyInt(), anyInt(), isNull());
    }

    @Test
    @DisplayName("6. Student - No class access → 403")
    void getAllChallengesForStudent_noAccess_403() throws Exception {
        when(submissionChallengeService.getAllChallengesForStudent(eq(CLASS_ID), eq(0), eq(10), isNull()))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/class/{classId}", CLASS_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(submissionChallengeService, times(1)).getAllChallengesForStudent(eq(CLASS_ID), anyInt(), anyInt(), isNull());
    }

    @Test
    @DisplayName("7. Student - Empty result → 200")
    void getAllChallengesForStudent_empty_200() throws Exception {
        List<StudentChallengeListDTO> emptyList = List.of();

        DataResponse<List<StudentChallengeListDTO>> response =
                DataResponse.success(emptyList, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                        .page(0).size(10).totalElements(0L).totalPages(0);

        when(submissionChallengeService.getAllChallengesForStudent(eq(CLASS_ID), eq(0), eq(10), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/challenge-submissions/class/{classId}", CLASS_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(submissionChallengeService, times(1)).getAllChallengesForStudent(anyLong(), anyInt(), anyInt(), isNull());
    }

    // ====================== GET /challenge/{challengeId}/submissions ======================

    @Test
    @DisplayName("8. Teacher - List submissions → 200")
    void getSubmissionsByChallenge_success_200() throws Exception {
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
    @DisplayName("9. Teacher - List submissions with text → 200")
    void getSubmissionsByChallenge_withText_200() throws Exception {
        DataResponse<List<StudentSubmissionDTO>> response = DataResponse.success(List.of(), Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL);

        when(submissionChallengeService.getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(0), eq(10), eq("Van A"), eq("createdAt"), eq("asc")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/challenge-submissions/challenge/{challengeId}/submissions", CHALLENGE_ID)
                        .param("text", "Van A")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk());

        verify(submissionChallengeService, times(1)).getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(0), eq(10), eq("Van A"), eq("createdAt"), eq("asc"));
    }

    @Test
    @DisplayName("10. Teacher - Invalid page → 400")
    void getSubmissionsByChallenge_invalidPage_400() throws Exception {
        when(submissionChallengeService.getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(-1), eq(10), isNull(), anyString(), anyString()))
                .thenThrow(new ApiException("Page must be >= 0", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/challenge/{challengeId}/submissions", CHALLENGE_ID)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page must be >= 0"));

        verify(submissionChallengeService, times(1)).getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(-1), eq(10), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("11. Teacher - Invalid size → 400")
    void getSubmissionsByChallenge_invalidSize_400() throws Exception {
        when(submissionChallengeService.getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(0), eq(0), isNull(), anyString(), anyString()))
                .thenThrow(new ApiException("Size must be > 0", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/challenge/{challengeId}/submissions", CHALLENGE_ID)
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Size must be > 0"));

        verify(submissionChallengeService, times(1)).getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(0), eq(0), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("12. Teacher - Invalid sortBy → 400")
    void getSubmissionsByChallenge_invalidSortBy_400() throws Exception {
        when(submissionChallengeService.getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(0), eq(10), isNull(), eq("invalid"), eq("asc")))
                .thenThrow(new ApiException("Invalid sortBy field", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/challenge/{challengeId}/submissions", CHALLENGE_ID)
                        .param("sortBy", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid sortBy field"));

        verify(submissionChallengeService, times(1)).getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(0), eq(10), isNull(), eq("invalid"), eq("asc"));
    }

    @Test
    @DisplayName("13. Teacher - Invalid sortDir → 400")
    void getSubmissionsByChallenge_invalidSortDir_400() throws Exception {
        when(submissionChallengeService.getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(0), eq(10), isNull(), eq("createdAt"), eq("invalid")))
                .thenThrow(new ApiException("Invalid sortDir", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/challenge/{challengeId}/submissions", CHALLENGE_ID)
                        .param("sortDir", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid sortDir"));

        verify(submissionChallengeService, times(1)).getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(0), eq(10), isNull(), eq("createdAt"), eq("invalid"));
    }

    @Test
    @DisplayName("14. Teacher - Challenge not found → 404")
    void getSubmissionsByChallenge_notFound_404() throws Exception {
        when(submissionChallengeService.getSubmissionsByChallenge(eq(999L), eq(0), eq(10), isNull(), anyString(), anyString()))
                .thenThrow(new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/challenge/999/submissions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.NOT_FOUND));

        verify(submissionChallengeService, times(1)).getSubmissionsByChallenge(eq(999L), anyInt(), anyInt(), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("15. Teacher - No class access → 403")
    void getSubmissionsByChallenge_noAccess_403() throws Exception {
        when(submissionChallengeService.getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(0), eq(10), isNull(), anyString(), anyString()))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/challenge/{challengeId}/submissions", CHALLENGE_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(submissionChallengeService, times(1)).getSubmissionsByChallenge(eq(CHALLENGE_ID), anyInt(), anyInt(), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("16. Teacher - Unauthorized role → 403")
    void getSubmissionsByChallenge_unauthorizedRole_403() throws Exception {
        when(submissionChallengeService.getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(0), eq(10), isNull(), anyString(), anyString()))
                .thenThrow(new ApiException(Const.SUBMISSION.UNAUTHORIZED_VIEW_SUBMISSIONS, HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/challenge/{challengeId}/submissions", CHALLENGE_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.UNAUTHORIZED_VIEW_SUBMISSIONS));

        verify(submissionChallengeService, times(1)).getSubmissionsByChallenge(eq(CHALLENGE_ID), anyInt(), anyInt(), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("17. Teacher - Empty result → 200")
    void getSubmissionsByChallenge_empty_200() throws Exception {

        List<StudentSubmissionDTO> emptyList = List.of();

        DataResponse<List<StudentSubmissionDTO>> response = DataResponse.success(emptyList, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .page(0).size(10).totalElements(0L).totalPages(0);

        when(submissionChallengeService.getSubmissionsByChallenge(eq(CHALLENGE_ID), eq(0), eq(10), isNull(), anyString(), anyString()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/challenge-submissions/challenge/{challengeId}/submissions", CHALLENGE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(submissionChallengeService, times(1)).getSubmissionsByChallenge(eq(CHALLENGE_ID), anyInt(), anyInt(), isNull(), anyString(), anyString());
    }

    // ====================== POST /submission/{submissionId}/start ======================

    @Test
    @DisplayName("18. Student starts submission → 200")
    void startSubmission_success_200() throws Exception {
        doNothing().when(submissionChallengeService).startSubmission(SUBMISSION_ID);

        mockMvc.perform(post("/api/v1/challenge-submissions/submission/{submissionId}/start", SUBMISSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));

        verify(submissionChallengeService, times(1)).startSubmission(SUBMISSION_ID);
    }

    @Test
    @DisplayName("19. Start submission - not found → 404")
    void startSubmission_notFound_404() throws Exception {
        doThrow(new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()))
                .when(submissionChallengeService).startSubmission(SUBMISSION_ID);

        mockMvc.perform(post("/api/v1/challenge-submissions/submission/{submissionId}/start", SUBMISSION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.NOT_FOUND));

        verify(submissionChallengeService, times(1)).startSubmission(SUBMISSION_ID);
    }

    @Test
    @DisplayName("20. Start submission - not owner → 403")
    void startSubmission_notOwner_403() throws Exception {
        doThrow(new ApiException(Const.SUBMISSION.FORBIDDEN_NOT_OWNER, HttpStatus.FORBIDDEN.value()))
                .when(submissionChallengeService).startSubmission(SUBMISSION_ID);

        mockMvc.perform(post("/api/v1/challenge-submissions/submission/{submissionId}/start", SUBMISSION_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.FORBIDDEN_NOT_OWNER));

        verify(submissionChallengeService, times(1)).startSubmission(SUBMISSION_ID);
    }

    @Test
    @DisplayName("21. Start submission - invalid status → 400")
    void startSubmission_invalidStatus_400() throws Exception {
        doThrow(new ApiException(Const.SUBMISSION.CANNOT_START_IN_CURRENT_STATUS, HttpStatus.BAD_REQUEST.value()))
                .when(submissionChallengeService).startSubmission(SUBMISSION_ID);

        mockMvc.perform(post("/api/v1/challenge-submissions/submission/{submissionId}/start", SUBMISSION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.CANNOT_START_IN_CURRENT_STATUS));

        verify(submissionChallengeService, times(1)).startSubmission(SUBMISSION_ID);
    }

    @Test
    @DisplayName("22. Start submission - invalid ID → 400")
    void startSubmission_invalidId_400() throws Exception {
        mockMvc.perform(post("/api/v1/challenge-submissions/submission/abc/start"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionChallengeService);
    }

    // ====================== GET /{submissionChallengeId}/info ======================

    @Test
    @DisplayName("23. Get submission info → 200")
    void getSubmissionInfo_success_200() throws Exception {
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
    @DisplayName("24. Get submission info - not found → 404")
    void getSubmissionInfo_notFound_404() throws Exception {
        when(submissionChallengeService.getSubmissionInfo(999L))
                .thenThrow(new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/999/info"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.NOT_FOUND));

        verify(submissionChallengeService, times(1)).getSubmissionInfo(999L);
    }

    @Test
    @DisplayName("25. Get submission info - no access → 403")
    void getSubmissionInfo_noAccess_403() throws Exception {
        when(submissionChallengeService.getSubmissionInfo(SUBMISSION_ID))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/challenge-submissions/{submissionChallengeId}/info", SUBMISSION_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(submissionChallengeService, times(1)).getSubmissionInfo(SUBMISSION_ID);
    }

    @Test
    @DisplayName("26. Get submission info - invalid ID → 400")
    void getSubmissionInfo_invalidId_400() throws Exception {
        mockMvc.perform(get("/api/v1/challenge-submissions/abc/info"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionChallengeService);
    }

//    @Test
//    @DisplayName("27. Get submission info - null ID → 400")
//    void getSubmissionInfo_nullId_400() throws Exception {
//        when(submissionChallengeService.getSubmissionInfo(null))
//                .thenThrow(new ApiException(Const.SUBMISSION.INVALID_ID, HttpStatus.BAD_REQUEST.value()));
//
//        mockMvc.perform(get("/api/v1/challenge-submissions/null/info"))
//                .andExpect(status().isBadRequest());
//
//        verifyNoInteractions(submissionChallengeService);  // Assuming path variable can't be null, but for coverage
//    }

    // ====================== POST /extend-deadline ======================

    @Test
    @DisplayName("28. Teacher - Extend deadline success → 200")
    void extendSubmissionDeadline_success_200() throws Exception {
        ExtendSubmissionDeadlineRequest request = ExtendSubmissionDeadlineRequest.builder()
                .submissionIds(List.of(SUBMISSION_ID))
                .newExpiredAt(OffsetDateTime.now().plusDays(1))
                .build();

        when(submissionChallengeService.extendSubmissionDeadline(any()))
                .thenReturn(String.format(Const.SUBMISSION.EXTEND_SUCCESS, 1));

        mockMvc.perform(post("/api/v1/challenge-submissions/extend-deadline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(String.format(Const.SUBMISSION.EXTEND_SUCCESS, 1)));

        verify(submissionChallengeService, times(1)).extendSubmissionDeadline(any());
    }

//    @Test
//    @DisplayName("29. Extend deadline - empty submissionIds → 400")
//    void extendSubmissionDeadline_emptyIds_400() throws Exception {
//        ExtendSubmissionDeadlineRequest request = ExtendSubmissionDeadlineRequest.builder()
//                .submissionIds(List.of())
//                .newExpiredAt(OffsetDateTime.now().plusDays(1))
//                .build();
//
//        when(submissionChallengeService.extendSubmissionDeadline(any()))
//                .thenThrow(new ApiException(Const.SUBMISSION.EMPTY_SUBMISSION_IDS, HttpStatus.BAD_REQUEST.value()));
//
//        mockMvc.perform(post("/api/v1/challenge-submissions/extend-deadline")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.SUBMISSION_IDS_REQUIRED_VN));
//
//        verify(submissionChallengeService, times(1)).extendSubmissionDeadline(any());
//    }

//    @Test
//    @DisplayName("30. Extend deadline - invalid newExpiredAt → 400")
//    void extendSubmissionDeadline_invalidDate_400() throws Exception {
//        ExtendSubmissionDeadlineRequest request = ExtendSubmissionDeadlineRequest.builder()
//                .submissionIds(List.of(SUBMISSION_ID))
//                .newExpiredAt(OffsetDateTime.now().minusDays(1))
//                .build();
//
//        when(submissionChallengeService.extendSubmissionDeadline(any()))
//                .thenThrow(new ApiException(Const.SUBMISSION.INVALID_EXTEND_TIME, HttpStatus.BAD_REQUEST.value()));
//
//        mockMvc.perform(post("/api/v1/challenge-submissions/extend-deadline")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.NEW_EXPIRED_AT_FUTURE_VN));
//
//        verify(submissionChallengeService, times(1)).extendSubmissionDeadline(any());
//    }

    @Test
    @DisplayName("31. Extend deadline - submissions not found → 404")
    void extendSubmissionDeadline_notFound_404() throws Exception {
        ExtendSubmissionDeadlineRequest request = ExtendSubmissionDeadlineRequest.builder()
                .submissionIds(List.of(999L))
                .newExpiredAt(OffsetDateTime.now().plusDays(1))
                .build();

        when(submissionChallengeService.extendSubmissionDeadline(any()))
                .thenThrow(new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(post("/api/v1/challenge-submissions/extend-deadline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.NOT_FOUND));

        verify(submissionChallengeService, times(1)).extendSubmissionDeadline(any());
    }

    @Test
    @DisplayName("32. Extend deadline - no access → 403")
    void extendSubmissionDeadline_noAccess_403() throws Exception {
        ExtendSubmissionDeadlineRequest request = ExtendSubmissionDeadlineRequest.builder()
                .submissionIds(List.of(SUBMISSION_ID))
                .newExpiredAt(OffsetDateTime.now().plusDays(1))
                .build();

        when(submissionChallengeService.extendSubmissionDeadline(any()))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(post("/api/v1/challenge-submissions/extend-deadline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(submissionChallengeService, times(1)).extendSubmissionDeadline(any());
    }

    @Test
    @DisplayName("33. Extend deadline - not eligible → 400")
    void extendSubmissionDeadline_notEligible_400() throws Exception {
        ExtendSubmissionDeadlineRequest request = ExtendSubmissionDeadlineRequest.builder()
                .submissionIds(List.of(SUBMISSION_ID))
                .newExpiredAt(OffsetDateTime.now().plusDays(1))
                .build();

        when(submissionChallengeService.extendSubmissionDeadline(any()))
                .thenThrow(new ApiException(String.format(Const.SUBMISSION.CANNOT_EXTEND_ELIGIBLE, SUBMISSION_ID), HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/challenge-submissions/extend-deadline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(submissionChallengeService, times(1)).extendSubmissionDeadline(any());
    }

    @Test
    @DisplayName("34. Extend deadline - malformed JSON → 400")
    void extendSubmissionDeadline_malformedJson_400() throws Exception {
        String malformedJson = "{ invalid json }";

        mockMvc.perform(post("/api/v1/challenge-submissions/extend-deadline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionChallengeService);
    }

    // ====================== POST /reset ======================

    @Test
    @DisplayName("35. Teacher - Reset submissions success → 200")
    void resetSubmissions_success_200() throws Exception {
        ResetSubmissionRequest request = ResetSubmissionRequest.builder()
                .submissionIds(List.of(SUBMISSION_ID))
                .newStartDate(OffsetDateTime.now().plusDays(1))
                .newEndDate(OffsetDateTime.now().plusDays(3))
                .build();

        when(submissionChallengeService.resetSubmissions(any()))
                .thenReturn(String.format(Const.SUBMISSION.RESET_SUCCESS, 1));

        mockMvc.perform(post("/api/v1/challenge-submissions/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(String.format(Const.SUBMISSION.RESET_SUCCESS, 1)));

        verify(submissionChallengeService, times(1)).resetSubmissions(any());
    }

//    @Test
//    @DisplayName("36. Reset submissions - empty submissionIds → 400")
//    void resetSubmissions_emptyIds_400() throws Exception {
//        ResetSubmissionRequest request = ResetSubmissionRequest.builder()
//                .submissionIds(List.of())
//                .newStartDate(OffsetDateTime.now().plusDays(1))
//                .newEndDate(OffsetDateTime.now().plusDays(3))
//                .build();
//
//        when(submissionChallengeService.resetSubmissions(any()))
//                .thenThrow(new ApiException(Const.SUBMISSION.EMPTY_SUBMISSION_IDS, HttpStatus.BAD_REQUEST.value()));
//
//        mockMvc.perform(post("/api/v1/challenge-submissions/reset")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.SUBMISSION_IDS_REQUIRED_VN));
//
//        verify(submissionChallengeService, times(1)).resetSubmissions(any());
//    }

    @Test
    @DisplayName("37. Reset submissions - invalid dates → 400")
    void resetSubmissions_invalidDates_400() throws Exception {
        ResetSubmissionRequest request = ResetSubmissionRequest.builder()
                .submissionIds(List.of(SUBMISSION_ID))
                .newStartDate(OffsetDateTime.now().plusDays(3))
                .newEndDate(OffsetDateTime.now().plusDays(1))
                .build();

        when(submissionChallengeService.resetSubmissions(any()))
                .thenThrow(new ApiException(Const.SUBMISSION.INVALID_RESET_DATES, HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/challenge-submissions/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.INVALID_RESET_DATES));

        verify(submissionChallengeService, times(1)).resetSubmissions(any());
    }

    @Test
    @DisplayName("38. Reset submissions - submissions not found → 404")
    void resetSubmissions_notFound_404() throws Exception {
        ResetSubmissionRequest request = ResetSubmissionRequest.builder()
                .submissionIds(List.of(999L))
                .newStartDate(OffsetDateTime.now().plusDays(1))
                .newEndDate(OffsetDateTime.now().plusDays(3))
                .build();

        when(submissionChallengeService.resetSubmissions(any()))
                .thenThrow(new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(post("/api/v1/challenge-submissions/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SUBMISSION.NOT_FOUND));

        verify(submissionChallengeService, times(1)).resetSubmissions(any());
    }

    @Test
    @DisplayName("39. Reset submissions - no access → 403")
    void resetSubmissions_noAccess_403() throws Exception {
        ResetSubmissionRequest request = ResetSubmissionRequest.builder()
                .submissionIds(List.of(SUBMISSION_ID))
                .newStartDate(OffsetDateTime.now().plusDays(1))
                .newEndDate(OffsetDateTime.now().plusDays(3))
                .build();

        when(submissionChallengeService.resetSubmissions(any()))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(post("/api/v1/challenge-submissions/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(submissionChallengeService, times(1)).resetSubmissions(any());
    }

    @Test
    @DisplayName("40. Reset submissions - not eligible (pending/draft) → 400")
    void resetSubmissions_notEligible_400() throws Exception {
        ResetSubmissionRequest request = ResetSubmissionRequest.builder()
                .submissionIds(List.of(SUBMISSION_ID))
                .newStartDate(OffsetDateTime.now().plusDays(1))
                .newEndDate(OffsetDateTime.now().plusDays(3))
                .build();

        when(submissionChallengeService.resetSubmissions(any()))
                .thenThrow(new ApiException(String.format(Const.SUBMISSION.CANNOT_RESET_PENDING_DRAFT, SUBMISSION_ID), HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/challenge-submissions/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(submissionChallengeService, times(1)).resetSubmissions(any());
    }

    @Test
    @DisplayName("41. Reset submissions - malformed JSON → 400")
    void resetSubmissions_malformedJson_400() throws Exception {
        String malformedJson = "{ invalid json }";

        mockMvc.perform(post("/api/v1/challenge-submissions/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionChallengeService);
    }
}