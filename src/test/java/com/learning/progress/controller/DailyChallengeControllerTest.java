package com.learning.progress.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.ChallengeMethod;
import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.common.ChallengeType;
import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.exception.GlobalExceptionHandler;
import com.learning.progress.service.DailyChallengeService;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DailyChallengeControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private DailyChallengeService dailyChallengeService;

    @InjectMocks
    private DailyChallengeController dailyChallengeController;

    private final Long VALID_CLASS_ID = 1L;
    private final Long VALID_CHALLENGE_ID = 100L;
    private final Long VALID_LESSON_ID = 10L;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        mockMvc = MockMvcBuilders.standaloneSetup(dailyChallengeController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }


    @DisplayName("1. Create - missing challengeName → 400")
    void create_missingChallengeName_400() throws Exception {
        String json = """
                {
                  "classLessonId": 10,
                  "description": "Test"
                }
                """;

        mockMvc.perform(post("/api/v1/daily-challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.NAME_REQUIRED));

        verifyNoInteractions(dailyChallengeService);
    }

    @Test
    @DisplayName("2. Create - missing classLessonId → 400")
    void create_missingClassLessonId_400() throws Exception {
        String json = """
                {
                  "challengeName": "Daily Challenge 1",
                  "challengeType": "RE"
                }
                """;

        mockMvc.perform(post("/api/v1/daily-challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.CLASS_LESSON_REQUIRED));

        verifyNoInteractions(dailyChallengeService);
    }

    @Test
    @DisplayName("3. Create - empty challengeName → 400")
    void create_emptyChallengeName_400() throws Exception {
        String json = """
                {
                  "challengeName": "",
                  "classLessonId": 10,
                  "challengeType": "RE"
                }
                """;

        mockMvc.perform(post("/api/v1/daily-challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.NAME_REQUIRED));

        verifyNoInteractions(dailyChallengeService);
    }

    @Test
    @DisplayName("4. Create - blank challengeName → 400")
    void create_blankChallengeName_400() throws Exception {
        String json = """
                {
                  "challengeName": "  ",
                  "classLessonId": 10,
                  "challengeType": "RE"
                }
                """;

        mockMvc.perform(post("/api/v1/daily-challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.NAME_REQUIRED));

        verifyNoInteractions(dailyChallengeService);
    }

//    @Test
//    @DisplayName("5. Create - lesson not found → 404")
//    void create_lessonNotFound_404() throws Exception {
//        CreateDailyChallengeRequest request = CreateDailyChallengeRequest.builder()
//                .challengeName("Morning Math")
//                .classLessonId(999L)
//                .build();
//
//        when(dailyChallengeService.createChallenge(any()))
//                .thenThrow(new ApiException(Const.CLASS_LESSON.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
//
//        mockMvc.perform(post("/api/v1/daily-challenges")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isNotFound())
//                .andExpect(jsonPath("$.error").value(Const.CLASS_LESSON.NOT_FOUND));
//
//        verify(dailyChallengeService, times(1)).createChallenge(any());
//    }
//
//    @Test
//    @DisplayName("6. Create - class not active → 400")
//    void create_classNotActive_400() throws Exception {
//        CreateDailyChallengeRequest request = CreateDailyChallengeRequest.builder()
//                .challengeName("Test")
//                .classLessonId(VALID_LESSON_ID)
//                .build();
//
//        when(dailyChallengeService.createChallenge(any()))
//                .thenThrow(new ApiException("Class is not active", HttpStatus.BAD_REQUEST.value()));
//
//        mockMvc.perform(post("/api/v1/daily-challenges")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.error").value("Class is not active"));
//
//        verify(dailyChallengeService, times(1)).createChallenge(any());
//    }

//    @Test
//    @DisplayName("7. Create - no class access → 403")
//    void create_noClassAccess_403() throws Exception {
//        CreateDailyChallengeRequest request = CreateDailyChallengeRequest.builder()
//                .challengeName("Test")
//                .classLessonId(VALID_LESSON_ID)
//                .build();
//
//        when(dailyChallengeService.createChallenge(any()))
//                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));
//
//        mockMvc.perform(post("/api/v1/daily-challenges")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isForbidden())  // ĐÃ SỬA: từ isBadRequest → isForbidden
//                .andExpect(jsonPath("$.error").value("Forbidden"));
//
//        verify(dailyChallengeService, times(1)).createChallenge(any());
//    }

//    @Test
//    @DisplayName("8. Create - duplicate name → 400")
//    void create_duplicateName_400() throws Exception {
//        CreateDailyChallengeRequest request = CreateDailyChallengeRequest.builder()
//                .challengeName("Existing Challenge")
//                .classLessonId(VALID_LESSON_ID)
//                .build();
//
//        when(dailyChallengeService.createChallenge(any()))
//                .thenThrow(new ApiException(
//                        String.format(Const.CHALLENGE.NAME_ALREADY_EXISTS_WITH_NAME, "Existing Challenge"),
//                        HttpStatus.BAD_REQUEST.value()));
//
//        mockMvc.perform(post("/api/v1/daily-challenges")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isBadRequest());
//
//        verify(dailyChallengeService, times(1)).createChallenge(any());
//    }

//    @Test
//    @DisplayName("9. Create - success with minimal fields → 201")
//    void create_minimalFields_success() throws Exception {
//        CreateDailyChallengeRequest request = CreateDailyChallengeRequest.builder()
//                .challengeName("Morning Math")
//                .classLessonId(VALID_LESSON_ID)
//                .build();
//
//        DailyChallengeResponse response = DailyChallengeResponse.builder()
//                .id(VALID_CHALLENGE_ID)
//                .challengeName("Morning Math")
//                .challengeStatus(ChallengeStatus.DRAFT)
//                .challengeMethod(ChallengeMethod.NORMAL)
//                .challengeType(ChallengeType.LI)
//                .build();
//
//        when(dailyChallengeService.createChallenge(any())).thenReturn(response);
//
//        mockMvc.perform(post("/api/v1/daily-challenges")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isCreated())
//                .andExpect(jsonPath("$.success").value(true))
//                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL))
//                .andExpect(jsonPath("$.data.id").value(VALID_CHALLENGE_ID.intValue()))
//                .andExpect(jsonPath("$.data.challengeStatus").value("DRAFT"));
//
//        verify(dailyChallengeService, times(1)).createChallenge(any());
//    }

    @Test
    @DisplayName("10. Create - success with full fields → 201")
    void create_fullFields_success() throws Exception {
        CreateDailyChallengeRequest request = CreateDailyChallengeRequest.builder()
                .challengeName("Complete Challenge")
                .classLessonId(VALID_LESSON_ID)
                .description("This is a complete challenge")
                .challengeType(ChallengeType.GV)
                .build();

        DailyChallengeResponse response = DailyChallengeResponse.builder()
                .id(VALID_CHALLENGE_ID)
                .challengeName("Complete Challenge")
                .description("This is a complete challenge")
                .challengeStatus(ChallengeStatus.DRAFT)
                .build();

        when(dailyChallengeService.createChallenge(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/daily-challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.challengeName").value("Complete Challenge"))
                .andExpect(jsonPath("$.data.description").value("This is a complete challenge"));

        verify(dailyChallengeService, times(1)).createChallenge(any());
    }

    @Test
    @DisplayName("12. Create - malformed JSON → 400")
    void create_malformedJson_400() throws Exception {
        String malformedJson = "{ invalid json }";

        mockMvc.perform(post("/api/v1/daily-challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dailyChallengeService);
    }

// ====================== GET LIST (GET /class/{classId}) ======================

    @Test
    @DisplayName("13. Get list - invalid page → 400")
    void getList_invalidPage_400() throws Exception {
        when(dailyChallengeService.getAllChallenges(
                eq(VALID_CLASS_ID), eq(-1), eq(10), isNull(), anyString(), anyString()))
                .thenThrow(new ApiException("Page must be >= 0", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/daily-challenges/class/1")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page must be >= 0"));

        verify(dailyChallengeService, times(1))
                .getAllChallenges(eq(VALID_CLASS_ID), eq(-1), eq(10), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("14. Get list - invalid size → 400")
    void getList_invalidSize_400() throws Exception {
        when(dailyChallengeService.getAllChallenges(
                eq(VALID_CLASS_ID), eq(0), eq(0), isNull(), anyString(), anyString()))
                .thenThrow(new ApiException("Size must be > 0", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/daily-challenges/class/1")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Size must be > 0"));

        verify(dailyChallengeService, times(1))
                .getAllChallenges(eq(VALID_CLASS_ID), eq(0), eq(0), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("15. Get list - invalid sortBy → 400")
    void getList_invalidSortBy_400() throws Exception {
        when(dailyChallengeService.getAllChallenges(
                eq(VALID_CLASS_ID), eq(0), eq(10), isNull(), eq("invalid"), eq("asc")))
                .thenThrow(new ApiException("Invalid sortBy field", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/daily-challenges/class/1")
                        .param("sortBy", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid sortBy field"));

        verify(dailyChallengeService, times(1))
                .getAllChallenges(eq(VALID_CLASS_ID), eq(0), eq(10), isNull(), eq("invalid"), eq("asc"));
    }

    @Test
    @DisplayName("16. Get list - invalid sortDir → 400")
    void getList_invalidSortDir_400() throws Exception {
        when(dailyChallengeService.getAllChallenges(
                eq(VALID_CLASS_ID), eq(0), eq(10), isNull(), eq("createdAt"), eq("invalid")))
                .thenThrow(new ApiException("Invalid sortDir", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/daily-challenges/class/1")
                        .param("sortDir", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid sortDir"));

        verify(dailyChallengeService, times(1))
                .getAllChallenges(eq(VALID_CLASS_ID), eq(0), eq(10), isNull(), eq("createdAt"), eq("invalid"));
    }

    @Test
    @DisplayName("17. Get list - class not found → 404")
    void getList_classNotFound_404() throws Exception {
        when(dailyChallengeService.getAllChallenges(
                eq(999L), eq(0), eq(10), isNull(), anyString(), anyString()))
                .thenThrow(new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/daily-challenges/class/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.CLASS.NOT_FOUND));

        verify(dailyChallengeService, times(1))
                .getAllChallenges(eq(999L), anyInt(), anyInt(), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("18. Get list - no class access → 403")
    void getList_noClassAccess_403() throws Exception {
        when(dailyChallengeService.getAllChallenges(
                eq(VALID_CLASS_ID), eq(0), eq(10), isNull(), anyString(), anyString()))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/daily-challenges/class/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(dailyChallengeService, times(1))
                .getAllChallenges(eq(VALID_CLASS_ID), anyInt(), anyInt(), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("19. Get list - empty result → 200")
    void getList_emptyResult_200() throws Exception {
        DataResponse<List<DailyChallengeListDTO>> response = DataResponse.<List<DailyChallengeListDTO>>builder()
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(List.of())
                .page(0).size(10).totalElements(0L).totalPages(0)
                .build();

        when(dailyChallengeService.getAllChallenges(
                eq(VALID_CLASS_ID), eq(0), eq(10), isNull(), eq("createdAt"), eq("asc")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/daily-challenges/class/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(dailyChallengeService, times(1))
                .getAllChallenges(anyLong(), anyInt(), anyInt(), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("20. Get list - success with default params → 200")
    void getList_defaultParams_success() throws Exception {
        DailyChallengeListDTO lessonDto = DailyChallengeListDTO.builder()
                .id(10L)
                .classLessonName("Lesson 1")
                .totalStudents(30L)
                .dailyChallenges(List.of(
                        DailyChallengeListDTO.DailyChallengeInLessonDTO.builder()
                                .id(100L)
                                .challengeName("Challenge A")
                                .submittedCount(25L)
                                .build()
                ))
                .build();

        DataResponse<List<DailyChallengeListDTO>> response = DataResponse.<List<DailyChallengeListDTO>>builder()
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(List.of(lessonDto))
                .page(0).size(10).totalElements(1L).totalPages(1)
                .build();

        when(dailyChallengeService.getAllChallenges(
                eq(VALID_CLASS_ID), eq(0), eq(10), isNull(), eq("createdAt"), eq("asc")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/daily-challenges/class/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].classLessonName").value("Lesson 1"))
                .andExpect(jsonPath("$.data[0].totalStudents").value(30))
                .andExpect(jsonPath("$.data[0].dailyChallenges[0].submittedCount").value(25));

        verify(dailyChallengeService, times(1))
                .getAllChallenges(anyLong(), anyInt(), anyInt(), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("21. Get list - success with custom params → 200")
    void getList_customParams_success() throws Exception {
        DataResponse<List<DailyChallengeListDTO>> response = DataResponse.<List<DailyChallengeListDTO>>builder()
                .success(true)
                .data(List.of())
                .page(2).size(20).totalElements(0L).totalPages(0)
                .build();

        when(dailyChallengeService.getAllChallenges(
                eq(VALID_CLASS_ID), eq(2), eq(20), eq("Math"), eq("challengeName"), eq("desc")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/daily-challenges/class/1")
                        .param("page", "2")
                        .param("size", "20")
                        .param("text", "Math")
                        .param("sortBy", "challengeName")
                        .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(20));

        verify(dailyChallengeService, times(1))
                .getAllChallenges(eq(VALID_CLASS_ID), eq(2), eq(20), eq("Math"), eq("challengeName"), eq("desc"));
    }

    @Test
    @DisplayName("22. Get list - with text filter → 200")
    void getList_withTextFilter_success() throws Exception {
        DataResponse<List<DailyChallengeListDTO>> response = DataResponse.<List<DailyChallengeListDTO>>builder()
                .success(true)
                .data(List.of())
                .page(0).size(10).totalElements(0L).totalPages(0)
                .build();

        when(dailyChallengeService.getAllChallenges(
                eq(VALID_CLASS_ID), eq(0), eq(10), eq("Math Challenge"), anyString(), anyString()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/daily-challenges/class/1")
                        .param("text", "Math Challenge"))
                .andExpect(status().isOk());

        verify(dailyChallengeService, times(1))
                .getAllChallenges(eq(VALID_CLASS_ID), eq(0), eq(10), eq("Math Challenge"), anyString(), anyString());
    }

// ====================== GET BY ID (GET /{id}) ======================

    @Test
    @DisplayName("23. Get by ID - not found → 404")
    void getById_notFound_404() throws Exception {
        when(dailyChallengeService.getChallengeById(999L))
                .thenThrow(new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/daily-challenges/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.NOT_FOUND));

        verify(dailyChallengeService, times(1)).getChallengeById(999L);
    }

    @Test
    @DisplayName("24. Get by ID - no class access → 403")
    void getById_noClassAccess_403() throws Exception {
        when(dailyChallengeService.getChallengeById(VALID_CHALLENGE_ID))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/daily-challenges/100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(dailyChallengeService, times(1)).getChallengeById(VALID_CHALLENGE_ID);
    }

    @Test
    @DisplayName("25. Get by ID - success → 200")
    void getById_success_200() throws Exception {
        DailyChallengeResponse response = DailyChallengeResponse.builder()
                .id(VALID_CHALLENGE_ID)
                .challengeName("Test Challenge")
                .description("Test Description")
                .challengeStatus(ChallengeStatus.PUBLISHED)
                .challengeMethod(ChallengeMethod.NORMAL)
                .build();

        when(dailyChallengeService.getChallengeById(VALID_CHALLENGE_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/daily-challenges/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.challengeName").value("Test Challenge"))
                .andExpect(jsonPath("$.data.challengeStatus").value("PUBLISHED"));

        verify(dailyChallengeService, times(1)).getChallengeById(VALID_CHALLENGE_ID);
    }

// ====================== UPDATE (PUT /{id}) ======================

    @Test
    @DisplayName("26. Update - missing challengeName → 400")
    void update_missingChallengeName_400() throws Exception {
        String json = """
                {
                  "challengeMethod": "NORMAL",
                  "startDate": "2025-12-01T12:00:00Z",
                  "endDate": "2025-12-05T12:00:00Z"
                }
                """;

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.NAME_REQUIRED));

        verifyNoInteractions(dailyChallengeService);
    }

    @Test
    @DisplayName("27. Update - missing challengeMethod → 400")
    void update_missingChallengeMethod_400() throws Exception {
        String json = """
                {
                  "challengeName": "Updated",
                  "startDate": "2025-12-01T12:00:00Z",
                  "endDate": "2025-12-05T12:00:00Z"
                }
                """;

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.METHOD_REQUIRED));

        verifyNoInteractions(dailyChallengeService);
    }

    @Test
    @DisplayName("28. Update - missing startDate → 400")
    void update_missingStartDate_400() throws Exception {
        String json = """
                {
                  "challengeName": "Updated",
                  "challengeMethod": "NORMAL",
                  "endDate": "2025-12-05T12:00:00Z"
                }
                """;

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.START_DATE_REQUIRED));

        verifyNoInteractions(dailyChallengeService);
    }

    @Test
    @DisplayName("29. Update - missing endDate → 400")
    void update_missingEndDate_400() throws Exception {
        String json = """
                {
                  "challengeName": "Updated",
                  "challengeMethod": "NORMAL",
                  "startDate": "2025-12-01T12:00:00Z"
                }
                """;

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.END_DATE_REQUIRED));

        verifyNoInteractions(dailyChallengeService);
    }

    @Test
    @DisplayName("30. Update - challenge not found → 404")
    void update_notFound_404() throws Exception {
        UpdateDailyChallengeDTO dto = createValidUpdateDto();

        when(dailyChallengeService.updateChallenge(eq(999L), any()))
                .thenThrow(new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(put("/api/v1/daily-challenges/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.NOT_FOUND));

        verify(dailyChallengeService, times(1)).updateChallenge(eq(999L), any());
    }

    // ====================== CREATE (POST /api/v1/daily-challenges) ======================

    @Test
    @DisplayName("31. Update - class not active → 400")
    void update_classNotActive_400() throws Exception {
        UpdateDailyChallengeDTO dto = createValidUpdateDto();

        when(dailyChallengeService.updateChallenge(eq(VALID_CHALLENGE_ID), any()))
                .thenThrow(new ApiException("Class is not active", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Class is not active"));

        verify(dailyChallengeService, times(1)).updateChallenge(eq(VALID_CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("32. Update - no class access → 403")
    void update_noClassAccess_403() throws Exception {
        UpdateDailyChallengeDTO dto = createValidUpdateDto();

        when(dailyChallengeService.updateChallenge(eq(VALID_CHALLENGE_ID), any()))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(dailyChallengeService, times(1)).updateChallenge(eq(VALID_CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("33. Update - duplicate name → 400")
    void update_duplicateName_400() throws Exception {
        UpdateDailyChallengeDTO dto = createValidUpdateDto();

        when(dailyChallengeService.updateChallenge(eq(VALID_CHALLENGE_ID), any()))
                .thenThrow(new ApiException(
                        String.format(Const.CHALLENGE.NAME_ALREADY_EXISTS_WITH_NAME, dto.getChallengeName()),
                        HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(dailyChallengeService, times(1)).updateChallenge(eq(VALID_CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("34. Update - endDate before startDate → 400")
    void update_invalidDateRange_400() throws Exception {
        UpdateDailyChallengeDTO dto = createValidUpdateDto();

        when(dailyChallengeService.updateChallenge(eq(VALID_CHALLENGE_ID), any()))
                .thenThrow(new ApiException(Const.CHALLENGE.INVALID_DATE_RANGE, HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.INVALID_DATE_RANGE));

        verify(dailyChallengeService, times(1)).updateChallenge(eq(VALID_CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("35. Update - cannot change startDate when IN_PROGRESS → 400")
    void update_cannotChangeStartDate_400() throws Exception {
        UpdateDailyChallengeDTO dto = createValidUpdateDto();

        when(dailyChallengeService.updateChallenge(eq(VALID_CHALLENGE_ID), any()))
                .thenThrow(new ApiException(Const.CHALLENGE.CANNOT_CHANGE_START_DATE, HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.CANNOT_CHANGE_START_DATE));

        verify(dailyChallengeService, times(1)).updateChallenge(eq(VALID_CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("36. Update - cannot change endDate when FINISHED → 400")
    void update_cannotChangeEndDate_400() throws Exception {
        UpdateDailyChallengeDTO dto = createValidUpdateDto();

        when(dailyChallengeService.updateChallenge(eq(VALID_CHALLENGE_ID), any()))
                .thenThrow(new ApiException(Const.CHALLENGE.CANNOT_CHANGE_END_DATE, HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.CANNOT_CHANGE_END_DATE));

        verify(dailyChallengeService, times(1)).updateChallenge(eq(VALID_CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("37. Update - TEST method without antiCheat → 400")
    void update_testWithoutAntiCheat_400() throws Exception {
        UpdateDailyChallengeDTO dto = createValidUpdateDto();
        dto.setChallengeMethod(ChallengeMethod.TEST);
        dto.setHasAntiCheat(false);
        dto.setDurationMinutes(60);

        when(dailyChallengeService.updateChallenge(eq(VALID_CHALLENGE_ID), any()))
                .thenThrow(new ApiException(
                        String.format(Const.CHALLENGE.TEST_CANNOT_DISABLE_ANTICHEAT, "Update"),
                        HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(dailyChallengeService, times(1)).updateChallenge(eq(VALID_CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("38. Update - TEST method with translateOnScreen → 400")
    void update_testWithTranslate_400() throws Exception {
        UpdateDailyChallengeDTO dto = createValidUpdateDto();
        dto.setChallengeMethod(ChallengeMethod.TEST);
        dto.setTranslateOnScreen(true);
        dto.setDurationMinutes(60);

        when(dailyChallengeService.updateChallenge(eq(VALID_CHALLENGE_ID), any()))
                .thenThrow(new ApiException(
                        String.format(Const.CHALLENGE.TEST_CANNOT_ENABLE_TRANSLATE, "Update"),
                        HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(dailyChallengeService, times(1)).updateChallenge(eq(VALID_CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("39. Update - TEST method without duration → 400")
    void update_testWithoutDuration_400() throws Exception {
        UpdateDailyChallengeDTO dto = createValidUpdateDto();
        dto.setChallengeMethod(ChallengeMethod.TEST);
        dto.setDurationMinutes(null);

        when(dailyChallengeService.updateChallenge(eq(VALID_CHALLENGE_ID), any()))
                .thenThrow(new ApiException(
                        String.format(Const.CHALLENGE.TEST_DURATION_REQUIRED, "Update"),
                        HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(dailyChallengeService, times(1)).updateChallenge(eq(VALID_CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("40. Update - success with NORMAL method → 200")
    void update_normalMethod_success() throws Exception {
        UpdateDailyChallengeDTO dto = createValidUpdateDto();
        dto.setChallengeMethod(ChallengeMethod.NORMAL);

        DailyChallengeResponse response = DailyChallengeResponse.builder()
                .id(VALID_CHALLENGE_ID)
                .challengeName(dto.getChallengeName())
                .challengeMethod(ChallengeMethod.NORMAL)
                .build();

        when(dailyChallengeService.updateChallenge(eq(VALID_CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL))
                .andExpect(jsonPath("$.data.challengeMethod").value("NORMAL"));

        verify(dailyChallengeService, times(1)).updateChallenge(eq(VALID_CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("41. Update - success with TEST method → 200")
    void update_testMethod_success() throws Exception {
        UpdateDailyChallengeDTO dto = createValidUpdateDto();
        dto.setChallengeMethod(ChallengeMethod.TEST);
        dto.setHasAntiCheat(true);
        dto.setTranslateOnScreen(false);
        dto.setDurationMinutes(90);

        DailyChallengeResponse response = DailyChallengeResponse.builder()
                .id(VALID_CHALLENGE_ID)
                .challengeMethod(ChallengeMethod.TEST)
                .durationMinutes(90)
                .build();

        when(dailyChallengeService.updateChallenge(eq(VALID_CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeMethod").value("TEST"))
                .andExpect(jsonPath("$.data.durationMinutes").value(90));

        verify(dailyChallengeService, times(1)).updateChallenge(eq(VALID_CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("42. Update - success with all optional fields → 200")
    void update_allFields_success() throws Exception {
        UpdateDailyChallengeDTO dto = createValidUpdateDto();
        dto.setDescription("Updated description");
        dto.setShuffleQuestion(false);

        DailyChallengeResponse response = DailyChallengeResponse.builder()
                .id(VALID_CHALLENGE_ID)
                .challengeName(dto.getChallengeName())
                .description("Updated description")
                .build();

        when(dailyChallengeService.updateChallenge(eq(VALID_CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Updated description"));

        verify(dailyChallengeService, times(1)).updateChallenge(eq(VALID_CHALLENGE_ID), any());
    }

    // ====================== PUBLISH (POST /{id}/publish) ======================

    @Test
    @DisplayName("43. Publish - challenge not found → 404")
    void publish_notFound_404() throws Exception {
        when(dailyChallengeService.publishChallenge(999L))
                .thenThrow(new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(post("/api/v1/daily-challenges/999/publish"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.NOT_FOUND));

        verify(dailyChallengeService, times(1)).publishChallenge(999L);
    }

    @Test
    @DisplayName("44. Publish - not in DRAFT status → 400")
    void publish_notDraft_400() throws Exception {
        when(dailyChallengeService.publishChallenge(VALID_CHALLENGE_ID))
                .thenThrow(new ApiException(
                        String.format(Const.CHALLENGE.NOT_DRAFT, ChallengeStatus.PUBLISHED),
                        HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/daily-challenges/100/publish"))
                .andExpect(status().isBadRequest());

        verify(dailyChallengeService, times(1)).publishChallenge(VALID_CHALLENGE_ID);
    }

    @Test
    @DisplayName("45. Publish - no sections → 400")
    void publish_noSections_400() throws Exception {
        when(dailyChallengeService.publishChallenge(VALID_CHALLENGE_ID))
                .thenThrow(new ApiException(Const.CHALLENGE.NO_SECTIONS, HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/daily-challenges/100/publish"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.NO_SECTIONS));

        verify(dailyChallengeService, times(1)).publishChallenge(VALID_CHALLENGE_ID);
    }

    @Test
    @DisplayName("46. Publish - class not active → 400")
    void publish_classNotActive_400() throws Exception {
        when(dailyChallengeService.publishChallenge(VALID_CHALLENGE_ID))
                .thenThrow(new ApiException("Class is not active", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/daily-challenges/100/publish"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Class is not active"));

        verify(dailyChallengeService, times(1)).publishChallenge(VALID_CHALLENGE_ID);
    }

    @Test
    @DisplayName("47. Publish - no class access → 403")
    void publish_noClassAccess_403() throws Exception {
        when(dailyChallengeService.publishChallenge(VALID_CHALLENGE_ID))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(post("/api/v1/daily-challenges/100/publish"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(dailyChallengeService, times(1)).publishChallenge(VALID_CHALLENGE_ID);
    }

    @Test
    @DisplayName("48. Publish - success → 200")
    void publish_success_200() throws Exception {
        DailyChallengeResponse response = DailyChallengeResponse.builder()
                .id(VALID_CHALLENGE_ID)
                .challengeStatus(ChallengeStatus.PUBLISHED)
                .challengeName("Test Challenge")
                .build();

        when(dailyChallengeService.publishChallenge(VALID_CHALLENGE_ID)).thenReturn(response);

        mockMvc.perform(post("/api/v1/daily-challenges/100/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL))
                .andExpect(jsonPath("$.data.challengeStatus").value("PUBLISHED"));

        verify(dailyChallengeService, times(1)).publishChallenge(VALID_CHALLENGE_ID);
    }

    // ====================== DELETE (DELETE /{id}) ======================

    @Test
    @DisplayName("49. Delete - challenge not found → 404")
    void delete_notFound_404() throws Exception {
        doThrow(new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value()))
                .when(dailyChallengeService).deleteChallenge(999L);

        mockMvc.perform(delete("/api/v1/daily-challenges/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.NOT_FOUND));

        verify(dailyChallengeService, times(1)).deleteChallenge(999L);
    }

    @Test
    @DisplayName("50. Delete - class not active → 400")
    void delete_classNotActive_400() throws Exception {
        doThrow(new ApiException("Class is not active", HttpStatus.BAD_REQUEST.value()))
                .when(dailyChallengeService).deleteChallenge(VALID_CHALLENGE_ID);

        mockMvc.perform(delete("/api/v1/daily-challenges/100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Class is not active"));

        verify(dailyChallengeService, times(1)).deleteChallenge(VALID_CHALLENGE_ID);
    }

    @Test
    @DisplayName("51. Delete - no class access → 403")
    void delete_noClassAccess_403() throws Exception {
        doThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()))
                .when(dailyChallengeService).deleteChallenge(VALID_CHALLENGE_ID);

        mockMvc.perform(delete("/api/v1/daily-challenges/100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(dailyChallengeService, times(1)).deleteChallenge(VALID_CHALLENGE_ID);
    }

    @Test
    @DisplayName("52. Delete - success → 200")
    void delete_success_200() throws Exception {
        doNothing().when(dailyChallengeService).deleteChallenge(VALID_CHALLENGE_ID);

        mockMvc.perform(delete("/api/v1/daily-challenges/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.DELETE_SUCCESSFUL));

        verify(dailyChallengeService, times(1)).deleteChallenge(VALID_CHALLENGE_ID);
    }

    // ====================== HIERARCHY (GET /{id}/hierarchy) ======================

    @Test
    @DisplayName("53. Get hierarchy - challenge not found → 404")
    void getHierarchy_notFound_404() throws Exception {
        when(dailyChallengeService.getChallengeHierarchy(999L))
                .thenThrow(new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/daily-challenges/999/hierarchy"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.NOT_FOUND));

        verify(dailyChallengeService, times(1)).getChallengeHierarchy(999L);
    }

    @Test
    @DisplayName("54. Get hierarchy - no class access → 403")
    void getHierarchy_noClassAccess_403() throws Exception {
        when(dailyChallengeService.getChallengeHierarchy(VALID_CHALLENGE_ID))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/daily-challenges/100/hierarchy"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(dailyChallengeService, times(1)).getChallengeHierarchy(VALID_CHALLENGE_ID);
    }

    @Test
    @DisplayName("55. Get hierarchy - success with full data → 200")
    void getHierarchy_fullData_success() throws Exception {
        DailyChallengeHierarchyDTO.LevelInfo level = DailyChallengeHierarchyDTO.LevelInfo.builder()
                .id(1L).levelName("Grade 1").levelCode("G1").description("First grade").orderNumber(1).status("ACTIVE").build();
        DailyChallengeHierarchyDTO.ChapterInfo chapter = DailyChallengeHierarchyDTO.ChapterInfo.builder()
                .id(5L).chapterName("Chapter 1").chapterCode("C1").orderNumber(1).build();
        DailyChallengeHierarchyDTO.LessonInfo lesson = DailyChallengeHierarchyDTO.LessonInfo.builder()
                .id(10L).lessonName("Lesson 1").lessonContent("Content").orderNumber(1).build();

        DailyChallengeHierarchyDTO hierarchy = DailyChallengeHierarchyDTO.builder()
                .level(level).chapter(chapter).lesson(lesson).build();

        when(dailyChallengeService.getChallengeHierarchy(VALID_CHALLENGE_ID)).thenReturn(hierarchy);

        mockMvc.perform(get("/api/v1/daily-challenges/100/hierarchy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.level.levelName").value("Grade 1"))
                .andExpect(jsonPath("$.data.chapter.chapterName").value("Chapter 1"))
                .andExpect(jsonPath("$.data.lesson.lessonName").value("Lesson 1"));

        verify(dailyChallengeService, times(1)).getChallengeHierarchy(VALID_CHALLENGE_ID);
    }

    @Test
    @DisplayName("56. Get hierarchy - success with null level → 200")
    void getHierarchy_nullLevel_success() throws Exception {
        DailyChallengeHierarchyDTO.ChapterInfo chapter = DailyChallengeHierarchyDTO.ChapterInfo.builder()
                .id(5L).chapterName("Chapter 1").build();
        DailyChallengeHierarchyDTO.LessonInfo lesson = DailyChallengeHierarchyDTO.LessonInfo.builder()
                .id(10L).lessonName("Lesson 1").build();

        DailyChallengeHierarchyDTO hierarchy = DailyChallengeHierarchyDTO.builder()
                .level(null).chapter(chapter).lesson(lesson).build();

        when(dailyChallengeService.getChallengeHierarchy(VALID_CHALLENGE_ID)).thenReturn(hierarchy);

        mockMvc.perform(get("/api/v1/daily-challenges/100/hierarchy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.level").isEmpty())
                .andExpect(jsonPath("$.data.chapter").isNotEmpty());

        verify(dailyChallengeService, times(1)).getChallengeHierarchy(VALID_CHALLENGE_ID);
    }

    // ====================== EXPORT WORKSHEET (GET /{id}/export-worksheet) ======================

    @Test
    @DisplayName("57. Export worksheet - challenge not found → 404")
    void exportWorksheet_notFound_404() throws Exception {
        when(dailyChallengeService.exportChallengeWorksheet(999L))
                .thenThrow(new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/daily-challenges/999/export-worksheet"))
                .andExpect(status().isNotFound());

        verify(dailyChallengeService, times(1)).exportChallengeWorksheet(999L);
    }

    @Test
    @DisplayName("58. Export worksheet - no class access → 403")
    void exportWorksheet_noClassAccess_403() throws Exception {
        when(dailyChallengeService.exportChallengeWorksheet(VALID_CHALLENGE_ID))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/daily-challenges/100/export-worksheet"))
                .andExpect(status().isForbidden());

        verify(dailyChallengeService, times(1)).exportChallengeWorksheet(VALID_CHALLENGE_ID);
    }

    @Test
    @DisplayName("59. Export worksheet - generation failed → 500")
    void exportWorksheet_generationFailed_500() throws Exception {
        when(dailyChallengeService.exportChallengeWorksheet(VALID_CHALLENGE_ID))
                .thenThrow(new RuntimeException("File generation error"));

        mockMvc.perform(get("/api/v1/daily-challenges/100/export-worksheet"))
                .andExpect(status().isInternalServerError());

        verify(dailyChallengeService, times(1)).exportChallengeWorksheet(VALID_CHALLENGE_ID);
    }

    @Test
    @DisplayName("60. Export worksheet - success → 200 with file")
    void exportWorksheet_success_200() throws Exception {
        byte[] mockFileContent = "Mock DOCX content".getBytes();

        when(dailyChallengeService.exportChallengeWorksheet(VALID_CHALLENGE_ID))
                .thenReturn(mockFileContent);

        mockMvc.perform(get("/api/v1/daily-challenges/100/export-worksheet"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().bytes(mockFileContent));

        verify(dailyChallengeService, times(1)).exportChallengeWorksheet(VALID_CHALLENGE_ID);
    }

    // ====================== HELPER METHODS ======================

    private UpdateDailyChallengeDTO createValidUpdateDto() {
        UpdateDailyChallengeDTO dto = new UpdateDailyChallengeDTO();
        dto.setChallengeName("Updated Challenge");
        dto.setDescription("Updated description");
        dto.setChallengeMethod(ChallengeMethod.NORMAL);
        dto.setDurationMinutes(null);
        dto.setHasAntiCheat(true);
        dto.setShuffleQuestion(true);
        dto.setTranslateOnScreen(false);
        dto.setStartDate(OffsetDateTime.now().plusDays(1));
        dto.setEndDate(OffsetDateTime.now().plusDays(3));
        return dto;
    }
}