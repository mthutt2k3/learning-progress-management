package com.learning.progress.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.ChallengeMethod;
import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.*;
import com.learning.progress.entity.*;
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
        objectMapper.findAndRegisterModules(); // for OffsetDateTime

        mockMvc = MockMvcBuilders.standaloneSetup(dailyChallengeController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ====================== CREATE (POST /api/v1/daily-challenges) ======================

    @Test
    @DisplayName("1. Create challenge - missing challengeName → 400")
    void create_missing_challengeName() throws Exception {
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
    @DisplayName("2. Create challenge - missing classLessonId → 400")
    void create_missing_classLessonId() throws Exception {
        String json = """
                {
                  "challengeName": "Daily Challenge 1"
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
    @DisplayName("3. Create challenge - success → 201")
    void create_success() throws Exception {
        CreateDailyChallengeRequest request = CreateDailyChallengeRequest.builder()
                .challengeName("Morning Math")
                .classLessonId(VALID_LESSON_ID)
                .description("Daily practice")
                .build();

        DailyChallengeResponse response = DailyChallengeResponse.builder()
                .id(VALID_CHALLENGE_ID)
                .challengeName("Morning Math")
                .challengeStatus(ChallengeStatus.DRAFT)
                .build();

        when(dailyChallengeService.createChallenge(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/daily-challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL))
                .andExpect(jsonPath("$.data.id").value(VALID_CHALLENGE_ID.intValue()));

        verify(dailyChallengeService, times(1)).createChallenge(any());
    }

    // ====================== GET LIST (/class/{classId}) ======================

    @Test
    @DisplayName("4. Get list - invalid page/size → 400 (validated in service)")
    void getList_invalid_pagination() throws Exception {
        // Service sẽ được gọi và ném ApiException → 400
        when(dailyChallengeService.getAllChallenges(
                eq(1L), eq(-1), eq(0), isNull(), anyString(), anyString()))
                .thenThrow(new ApiException("Page must be >= 0", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/daily-challenges/class/1")
                        .param("page", "-1")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page must be >= 0")); // hoặc message thực tế

        verify(dailyChallengeService, times(1))
                .getAllChallenges(eq(1L), eq(-1), eq(0), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("5. Get list - success → 200")
    void getList_success() throws Exception {
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
                .page(0)
                .size(10)
                .totalElements(1L)
                .totalPages(1)
                .build();

        when(dailyChallengeService.getAllChallenges(eq(VALID_CLASS_ID), eq(0), eq(10), isNull(), eq("createdAt"), eq("asc")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/daily-challenges/class/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].dailyChallenges[0].submittedCount").value(25));

        verify(dailyChallengeService, times(1)).getAllChallenges(anyLong(), anyInt(), anyInt(), isNull(), anyString(), anyString());
    }

    // ====================== GET BY ID (/ {id}) ======================

    @Test
    @DisplayName("6. Get by ID - not found → 404")
    void getById_notFound() throws Exception {
        when(dailyChallengeService.getChallengeById(999L))
                .thenThrow(new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/daily-challenges/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.NOT_FOUND));

        verify(dailyChallengeService, times(1)).getChallengeById(999L);
    }

    @Test
    @DisplayName("7. Get by ID - success → 200")
    void getById_success() throws Exception {
        DailyChallengeResponse response = DailyChallengeResponse.builder()
                .id(VALID_CHALLENGE_ID)
                .challengeName("Test Challenge")
                .challengeStatus(ChallengeStatus.PUBLISHED)
                .build();

        when(dailyChallengeService.getChallengeById(VALID_CHALLENGE_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/daily-challenges/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));

        verify(dailyChallengeService, times(1)).getChallengeById(VALID_CHALLENGE_ID);
    }

    // ====================== UPDATE (PUT /{id}) ======================

    @Test
    @DisplayName("8. Update - missing startDate → 400")
    void update_missing_startDate() throws Exception {
        String json = """
                {
                  "challengeName": "Updated",
                  "challengeMethod": "NORMAL",
                  "endDate": "2025-12-01T12:00:00Z"
                }
                """;

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Start date cannot be empty"));

        verifyNoInteractions(dailyChallengeService);
    }

    @Test
    @DisplayName("9. Update - success → 200")
    void update_success() throws Exception {
        UpdateDailyChallengeDTO dto = new UpdateDailyChallengeDTO();
        dto.setChallengeName("Updated Challenge");
        dto.setChallengeMethod(ChallengeMethod.NORMAL);
        dto.setStartDate(OffsetDateTime.now().plusDays(1));
        dto.setEndDate(OffsetDateTime.now().plusDays(3));

        DailyChallengeResponse response = DailyChallengeResponse.builder()
                .id(VALID_CHALLENGE_ID)
                .challengeName("Updated Challenge")
                .build();

        when(dailyChallengeService.updateChallenge(eq(VALID_CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/daily-challenges/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL))
                .andExpect(jsonPath("$.data.challengeName").value("Updated Challenge"));

        verify(dailyChallengeService, times(1)).updateChallenge(anyLong(), any());
    }

    // ====================== PUBLISH (POST /{id}/publish) ======================

    @Test
    @DisplayName("10. Publish - not draft → 400")
    void publish_notDraft() throws Exception {
        when(dailyChallengeService.publishChallenge(VALID_CHALLENGE_ID))
                .thenThrow(new ApiException("Challenge is not in DRAFT status", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/daily-challenges/100/publish"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Challenge is not in DRAFT status"));

        verify(dailyChallengeService, times(1)).publishChallenge(VALID_CHALLENGE_ID);
    }

    @Test
    @DisplayName("11. Publish - success → 200")
    void publish_success() throws Exception {
        DailyChallengeResponse response = DailyChallengeResponse.builder()
                .id(VALID_CHALLENGE_ID)
                .challengeStatus(ChallengeStatus.PUBLISHED)
                .build();

        when(dailyChallengeService.publishChallenge(VALID_CHALLENGE_ID)).thenReturn(response);

        mockMvc.perform(post("/api/v1/daily-challenges/100/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeStatus").value("PUBLISHED"));

        verify(dailyChallengeService, times(1)).publishChallenge(VALID_CHALLENGE_ID);
    }

    // ====================== DELETE (DELETE /{id}) ======================

    @Test
    @DisplayName("12. Delete - success → 200")
    void delete_success() throws Exception {
        doNothing().when(dailyChallengeService).deleteChallenge(VALID_CHALLENGE_ID);

        mockMvc.perform(delete("/api/v1/daily-challenges/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.DELETE_SUCCESSFUL));

        verify(dailyChallengeService, times(1)).deleteChallenge(VALID_CHALLENGE_ID);
    }

    // ====================== HIERARCHY (GET /{id}/hierarchy) ======================

    @Test
    @DisplayName("13. Get hierarchy - success → 200")
    void getHierarchy_success() throws Exception {
        DailyChallengeHierarchyDTO.LevelInfo level = DailyChallengeHierarchyDTO.LevelInfo.builder()
                .id(1L).levelName("Grade 1").build();
        DailyChallengeHierarchyDTO.ChapterInfo chapter = DailyChallengeHierarchyDTO.ChapterInfo.builder()
                .id(5L).chapterName("Chapter 1").build();
        DailyChallengeHierarchyDTO.LessonInfo lesson = DailyChallengeHierarchyDTO.LessonInfo.builder()
                .id(10L).lessonName("Lesson 1").build();

        DailyChallengeHierarchyDTO hierarchy = DailyChallengeHierarchyDTO.builder()
                .level(level).chapter(chapter).lesson(lesson).build();

        when(dailyChallengeService.getChallengeHierarchy(VALID_CHALLENGE_ID)).thenReturn(hierarchy);

        mockMvc.perform(get("/api/v1/daily-challenges/100/hierarchy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.level.levelName").value("Grade 1"))
                .andExpect(jsonPath("$.data.chapter.chapterName").value("Chapter 1"));

        verify(dailyChallengeService, times(1)).getChallengeHierarchy(VALID_CHALLENGE_ID);
    }

    // ====================== EXTRA FIELD IGNORED ======================

    @Test
    @DisplayName("14. Extra field in create → ignored, success")
    void create_extraField_ignored() throws Exception {
        String json = """
                {
                  "challengeName": "Test",
                  "classLessonId": 10,
                  "extraField": "should be ignored"
                }
                """;

        DailyChallengeResponse response = DailyChallengeResponse.builder()
                .id(200L)
                .challengeName("Test")
                .build();

        when(dailyChallengeService.createChallenge(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/daily-challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(200));

        verify(dailyChallengeService, times(1)).createChallenge(any());
    }
}