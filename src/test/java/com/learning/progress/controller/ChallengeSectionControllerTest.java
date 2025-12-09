package com.learning.progress.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.section.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.exception.GlobalExceptionHandler;
import com.learning.progress.service.ChallengeSectionService;
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
class ChallengeSectionControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ChallengeSectionService sectionService;

    @InjectMocks
    private ChallengeSectionController sectionController;

    private final Long CHALLENGE_ID = 100L;
    private final Long SECTION_ID = 200L;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        mockMvc = MockMvcBuilders.standaloneSetup(sectionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ====================== SAVE SINGLE SECTION (POST /{challengeId}) ======================

    @Test
    @DisplayName("1. Save section - null section → 400")
    void saveSection_nullSection_400() throws Exception {
        String json = """
                { "section": null, "questions": [ { "questionText": "Q1", "orderNumber": 1, "questionType": "MCQ", "weight": 1.0 } ] }
                """;

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException(Const.SECTION.SECTION_REQUIRED, HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.SECTION.SECTION_REQUIRED));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("2. Save section - empty questions → 400")
    void saveSection_emptyQuestions_400() throws Exception {
        String json = """
                { "section": { "sectionTitle": "Part 1", "resourceType": "TEXT" }, "questions": [] }
                """;

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException(Const.SECTION.QUESTIONS_REQUIRED, HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.SECTION.QUESTIONS_REQUIRED));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("3. Save section - challenge not found → 404")
    void saveSection_challengeNotFound_404() throws Exception {
        SectionWithQuestionsDto request = createSampleSectionWithQuestions("Test", 1);

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.NOT_FOUND));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("4. Save section - no class access → 403")
    void saveSection_noClassAccess_403() throws Exception {
        SectionWithQuestionsDto request = createSampleSectionWithQuestions("Test", 1);

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("5. Save section - invalid resourceType → 400")
    void saveSection_invalidResourceType_400() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Test", null, "Content", 1, "INVALID_TYPE");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException("Invalid enum value", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid enum value"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("6. Save section - class not active → 400")
    void saveSection_classNotActive_400() throws Exception {
        SectionWithQuestionsDto request = createSampleSectionWithQuestions("Test", 1);

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException("Class is not active", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Class is not active"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("7. Save section - create new on draft challenge → 200")
    void saveSection_createNewDraft_success() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Math Part 1", null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "2+2=?", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "4", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).sectionTitle("Math Part 1").build())
                .questions(List.of(QuestionDto.builder().id(300L).questionText("2+2=?").build()))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.section.id").value(SECTION_ID.intValue()));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("8. Save section - update existing on draft → 200")
    void saveSection_updateDraft_success() throws Exception {
        SectionDto sectionDto = new SectionDto(SECTION_ID, "Updated Title", null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(300L, "Updated?", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).sectionTitle("Updated Title").build())
                .questions(List.of(QuestionDto.builder().id(300L).questionText("Updated?").build()))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section.sectionTitle").value("Updated Title"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("9. Save section - create new on published challenge → 400")
    void saveSection_createNewPublished_forbidden() throws Exception {
        SectionWithQuestionsDto request = createSampleSectionWithQuestions("New", 1);

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException(Const.SECTION.CANNOT_CREATE_FOR_PUBLISHED, HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.SECTION.CANNOT_CREATE_FOR_PUBLISHED));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("10. Save section - update on published, questions changed → trigger regrade")
    void saveSection_updatePublished_questionsChanged() throws Exception {
        SectionDto sectionDto = new SectionDto(SECTION_ID, "Updated", null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(300L, "New Text", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).build())
                .questions(List.of(q))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("11. Save section - update on published, questions unchanged → no regrade")
    void saveSection_updatePublished_questionsUnchanged() throws Exception {
        SectionDto sectionDto = new SectionDto(SECTION_ID, "Same", null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(300L, "Same", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).build())
                .questions(List.of(q))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("12. Save section - question validation fails → 400")
    void saveSection_questionValidationFails_400() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Test", null, "Content", 1, "TEXT");
        QuestionDto invalidQ = new QuestionDto(null, "", 1, 1.0, "MCQ",
                new DataContent(List.of()), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(invalidQ));

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException("Question text is empty", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Question text is empty"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("13. Save section - extra fields ignored")
    void saveSection_extraFieldIgnored() throws Exception {
        String json = """
                {
                  "section": { "sectionTitle": "Test", "resourceType": "TEXT" },
                  "questions": [ { "questionText": "Q1", "orderNumber": 1, "questionType": "MCQ", "weight": 1.0, "content": {"data": []} } ],
                  "extraField": "ignored"
                }
                """;

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(999L).build())
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section.id").value(999));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    // ====================== BULK SAVE (POST /bulk-save/{challengeId}) ======================

    @Test
    @DisplayName("14. Bulk save - success → 201")
    void bulkSave_success() throws Exception {
        SectionWithQuestionsDto dto1 = createSampleSectionWithQuestions("Sec1", 1);
        SectionWithQuestionsDto dto2 = createSampleSectionWithQuestions("Sec2", 2);
        List<SectionWithQuestionsDto> request = List.of(dto1, dto2);

        List<SectionWithQuestionsDto> response = List.of(
                SectionWithQuestionsDto.builder().section(SectionDto.builder().id(201L).build()).build(),
                SectionWithQuestionsDto.builder().section(SectionDto.builder().id(202L).build()).build()
        );

        when(sectionService.saveSectionList(eq(CHALLENGE_ID), anyList())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/bulk-save/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].section.id").value(201))
                .andExpect(jsonPath("$.data[1].section.id").value(202));

        verify(sectionService, times(1)).saveSectionList(eq(CHALLENGE_ID), anyList());
    }

    @Test
    @DisplayName("15. Bulk save - empty list → 400")
    void bulkSave_emptyList_400() throws Exception {
        when(sectionService.saveSectionList(eq(CHALLENGE_ID), anyList()))
                .thenThrow(new ApiException("Request list cannot be empty", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/sections/bulk-save/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest());

        verify(sectionService, times(1)).saveSectionList(eq(CHALLENGE_ID), anyList());
    }

    // ====================== BULK ORDER/DELETE (POST /bulk/{challengeId}) ======================

    @Test
    @DisplayName("16. Bulk order - invalid section ID → 400")
    void bulkOrder_invalidSectionId_400() throws Exception {
        QuickBulkSectionRequest req = new QuickBulkSectionRequest(999L, 1, false);

        doThrow(new ApiException("Invalid section IDs: [999]", HttpStatus.BAD_REQUEST.value()))
                .when(sectionService).bulkOrderSection(eq(CHALLENGE_ID), anyList());

        mockMvc.perform(post("/api/v1/sections/bulk/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(req))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid section IDs: [999]"));

        verify(sectionService, times(1)).bulkOrderSection(eq(CHALLENGE_ID), anyList());
    }

    @Test
    @DisplayName("17. Bulk delete - published challenge → 400")
    void bulkDelete_publishedChallenge_400() throws Exception {
        QuickBulkSectionRequest req = new QuickBulkSectionRequest(200L, null, true);

        doThrow(new ApiException(Const.SECTION.CANNOT_DELETE_FOR_PUBLISHED, HttpStatus.BAD_REQUEST.value()))
                .when(sectionService).bulkOrderSection(eq(CHALLENGE_ID), anyList());

        mockMvc.perform(post("/api/v1/sections/bulk/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(req))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.SECTION.CANNOT_DELETE_FOR_PUBLISHED));

        verify(sectionService, times(1)).bulkOrderSection(eq(CHALLENGE_ID), anyList());
    }

    @Test
    @DisplayName("18. Bulk order - missing sections → 400")
    void bulkOrder_missingSections_400() throws Exception {
        List<QuickBulkSectionRequest> req = List.of(
                new QuickBulkSectionRequest(201L, 1, false)
        );

        doThrow(new ApiException("Sections not handled: [202]", HttpStatus.BAD_REQUEST.value()))
                .when(sectionService).bulkOrderSection(eq(CHALLENGE_ID), anyList());

        mockMvc.perform(post("/api/v1/sections/bulk/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Sections not handled: [202]"));

        verify(sectionService, times(1)).bulkOrderSection(eq(CHALLENGE_ID), anyList());
    }

    @Test
    @DisplayName("19. Bulk order - invalid order numbers → 400")
    void bulkOrder_invalidOrderNumbers_400() throws Exception {
        List<QuickBulkSectionRequest> req = List.of(
                new QuickBulkSectionRequest(201L, 1, false),
                new QuickBulkSectionRequest(202L, 3, false) // skip 2
        );

        doThrow(new ApiException("Order numbers must be sequential", HttpStatus.BAD_REQUEST.value()))
                .when(sectionService).bulkOrderSection(eq(CHALLENGE_ID), anyList());

        mockMvc.perform(post("/api/v1/sections/bulk/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Order numbers must be sequential"));

        verify(sectionService, times(1)).bulkOrderSection(eq(CHALLENGE_ID), anyList());
    }

    @Test
    @DisplayName("20. Bulk order - success → 200")
    void bulkOrder_success() throws Exception {
        List<QuickBulkSectionRequest> req = List.of(
                new QuickBulkSectionRequest(201L, 2, false),
                new QuickBulkSectionRequest(202L, 1, false)
        );

        doNothing().when(sectionService).bulkOrderSection(eq(CHALLENGE_ID), anyList());

        mockMvc.perform(post("/api/v1/sections/bulk/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL));

        verify(sectionService, times(1)).bulkOrderSection(eq(CHALLENGE_ID), anyList());
    }

    @Test
    @DisplayName("21. Bulk delete - success → 200")
    void bulkDelete_success() throws Exception {
        List<QuickBulkSectionRequest> req = List.of(
                new QuickBulkSectionRequest(201L, null, true),
                new QuickBulkSectionRequest(202L, 1, false)
        );

        doNothing().when(sectionService).bulkOrderSection(eq(CHALLENGE_ID), anyList());

        mockMvc.perform(post("/api/v1/sections/bulk/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sectionService, times(1)).bulkOrderSection(eq(CHALLENGE_ID), anyList());
    }

    // ====================== GET BY ID (GET /{id}) ======================

    @Test
    @DisplayName("22. Get section - not found → 404")
    void getSection_notFound_404() throws Exception {
        when(sectionService.getSection(999L))
                .thenThrow(new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/sections/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SECTION.NOT_FOUND));

        verify(sectionService, times(1)).getSection(999L);
    }

    @Test
    @DisplayName("23. Get section - no class access → 403")
    void getSection_noClassAccess_403() throws Exception {
        when(sectionService.getSection(SECTION_ID))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/sections/{id}", SECTION_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(sectionService, times(1)).getSection(SECTION_ID);
    }

    @Test
    @DisplayName("24. Get section - success → 200")
    void getSection_success() throws Exception {
        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).sectionTitle("Part A").build())
                .questions(List.of(QuestionDto.builder().id(301L).questionText("Q1").build()))
                .build();

        when(sectionService.getSection(SECTION_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/sections/{id}", SECTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section.id").value(SECTION_ID.intValue()))
                .andExpect(jsonPath("$.data.questions[0].id").value(301));

        verify(sectionService, times(1)).getSection(SECTION_ID);
    }

    // ====================== LIST SECTIONS (GET /challenge/{challengeId}) ======================

    @Test
    @DisplayName("25. List sections - invalid page → 400")
    void listSections_invalidPage_400() throws Exception {
        when(sectionService.listSections(eq(CHALLENGE_ID), eq(-1), eq(10), isNull()))
                .thenThrow(new ApiException("Page must be >= 0", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/sections/challenge/{challengeId}", CHALLENGE_ID)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page must be >= 0"));

        verify(sectionService, times(1)).listSections(eq(CHALLENGE_ID), eq(-1), eq(10), isNull());
    }

    @Test
    @DisplayName("26. List sections - invalid size → 400")
    void listSections_invalidSize_400() throws Exception {
        when(sectionService.listSections(eq(CHALLENGE_ID), eq(0), eq(0), isNull()))
                .thenThrow(new ApiException("Size must be > 0", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/api/v1/sections/challenge/{challengeId}", CHALLENGE_ID)
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Size must be > 0"));

        verify(sectionService, times(1)).listSections(eq(CHALLENGE_ID), eq(0), eq(0), isNull());
    }

    @Test
    @DisplayName("27. List sections - success with pagination → 200")
    void listSections_successWithPagination() throws Exception {
        SectionWithQuestionsDto dto1 = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(201L).sectionTitle("Sec1").build())
                .questions(List.of(QuestionDto.builder().id(301L).build()))
                .build();

        SectionWithQuestionsDto dto2 = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(202L).sectionTitle("Sec2").build())
                .questions(List.of(QuestionDto.builder().id(302L).build()))
                .build();

        DataResponse<List<SectionWithQuestionsDto>> response = DataResponse.<List<SectionWithQuestionsDto>>builder()
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(List.of(dto1, dto2))
                .page(0).size(10).totalElements(2L).totalPages(1)
                .build();

        when(sectionService.listSections(eq(CHALLENGE_ID), eq(0), eq(10), isNull())).thenReturn(response);

        mockMvc.perform(get("/api/v1/sections/challenge/{challengeId}", CHALLENGE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].section.id").value(201))
                .andExpect(jsonPath("$.data[1].section.id").value(202))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(2));

        verify(sectionService, times(1)).listSections(eq(CHALLENGE_ID), eq(0), eq(10), isNull());
    }

    @Test
    @DisplayName("28. List sections - with text filter → 200")
    void listSections_withTextFilter() throws Exception {
        SectionWithQuestionsDto dto = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(201L).sectionTitle("Math").build())
                .build();

        DataResponse<List<SectionWithQuestionsDto>> response = DataResponse.<List<SectionWithQuestionsDto>>builder()
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(List.of(dto))
                .page(0).size(10).totalElements(1L).totalPages(1)
                .build();

        when(sectionService.listSections(eq(CHALLENGE_ID), eq(0), eq(10), eq("Math"))).thenReturn(response);

        mockMvc.perform(get("/api/v1/sections/challenge/{challengeId}", CHALLENGE_ID)
                        .param("text", "Math"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].section.sectionTitle").value("Math"));

        verify(sectionService, times(1)).listSections(eq(CHALLENGE_ID), eq(0), eq(10), eq("Math"));
    }

    @Test
    @DisplayName("29. List sections - empty result → 200")
    void listSections_emptyResult() throws Exception {
        DataResponse<List<SectionWithQuestionsDto>> response = DataResponse.<List<SectionWithQuestionsDto>>builder()
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(List.of())
                .page(0).size(10).totalElements(0L).totalPages(0)
                .build();

        when(sectionService.listSections(eq(CHALLENGE_ID), eq(0), eq(10), isNull())).thenReturn(response);

        mockMvc.perform(get("/api/v1/sections/challenge/{challengeId}", CHALLENGE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(sectionService, times(1)).listSections(eq(CHALLENGE_ID), eq(0), eq(10), isNull());
    }

    // ====================== LIST PUBLIC SECTIONS (GET /challenge/{challengeId}/public) ======================

    @Test
    @DisplayName("30. List public sections - success → 200")
    void listPublicSections_success() throws Exception {
        StudentSectionWithQuestionsDto dto = StudentSectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(201L).sectionTitle("Sec1").build())
                .questions(List.of(StudentSectionWithQuestionsDto.StudentQuestionDto.builder()
                        .id(301L).questionText("Q1").questionType("MCQ").build()))
                .build();

        DataResponse<List<StudentSectionWithQuestionsDto>> response = DataResponse.success(List.of(dto), "OK")
                .page(0).size(10).totalElements(1L).totalPages(1);

        when(sectionService.listSectionsWithoutAnswers(eq(CHALLENGE_ID), eq(0), eq(10), isNull())).thenReturn(response);

        mockMvc.perform(get("/api/v1/sections/challenge/{challengeId}/public", CHALLENGE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].questions[0].id").value(301))
                .andExpect(jsonPath("$.data[0].questions[0].content").doesNotExist());

        verify(sectionService, times(1)).listSectionsWithoutAnswers(eq(CHALLENGE_ID), eq(0), eq(10), isNull());
    }

    @Test
    @DisplayName("31. List public sections - with pagination → 200")
    void listPublicSections_withPagination() throws Exception {
        StudentSectionWithQuestionsDto dto1 = StudentSectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(201L).build())
                .build();

        StudentSectionWithQuestionsDto dto2 = StudentSectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(202L).build())
                .build();

        DataResponse<List<StudentSectionWithQuestionsDto>> response = DataResponse.success(List.of(dto1, dto2), "OK")
                .page(1).size(5).totalElements(10L).totalPages(2);

        when(sectionService.listSectionsWithoutAnswers(eq(CHALLENGE_ID), eq(1), eq(5), isNull())).thenReturn(response);

        mockMvc.perform(get("/api/v1/sections/challenge/{challengeId}/public", CHALLENGE_ID)
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(sectionService, times(1)).listSectionsWithoutAnswers(eq(CHALLENGE_ID), eq(1), eq(5), isNull());
    }

    @Test
    @DisplayName("32. List public sections - no access → 403")
    void listPublicSections_noAccess_403() throws Exception {
        when(sectionService.listSectionsWithoutAnswers(eq(CHALLENGE_ID), eq(0), eq(10), isNull()))
                .thenThrow(new ApiException("Forbidden", HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(get("/api/v1/sections/challenge/{challengeId}/public", CHALLENGE_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(sectionService, times(1)).listSectionsWithoutAnswers(eq(CHALLENGE_ID), eq(0), eq(10), isNull());
    }

    @Test
    @DisplayName("33. List public sections - challenge not found → 404")
    void listPublicSections_challengeNotFound_404() throws Exception {
        when(sectionService.listSectionsWithoutAnswers(eq(CHALLENGE_ID), eq(0), eq(10), isNull()))
                .thenThrow(new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/sections/challenge/{challengeId}/public", CHALLENGE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.CHALLENGE.NOT_FOUND));

        verify(sectionService, times(1)).listSectionsWithoutAnswers(eq(CHALLENGE_ID), eq(0), eq(10), isNull());
    }

    // ====================== EDGE CASES ======================

    @Test
    @DisplayName("34. Save section - malformed JSON → 400")
    void saveSection_malformedJson_400() throws Exception {
        String malformedJson = "{ invalid json }";

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sectionService);
    }

    @Test
    @DisplayName("35. Bulk order - empty list → 400")
    void bulkOrder_emptyList_400() throws Exception {
        doThrow(new ApiException("Request list cannot be empty", HttpStatus.BAD_REQUEST.value()))
                .when(sectionService).bulkOrderSection(eq(CHALLENGE_ID), anyList());

        mockMvc.perform(post("/api/v1/sections/bulk/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest());

        verify(sectionService, times(1)).bulkOrderSection(eq(CHALLENGE_ID), anyList());
    }

    @Test
    @DisplayName("36. Save section - very large payload → 200")
    void saveSection_largePayload_success() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Test", null, "Content", 1, "TEXT");

        // Create 100 questions
        List<QuestionDto> questions = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            questions.add(new QuestionDto(null, "Q" + i, i + 1, 1.0, "MCQ",
                    new DataContent(List.of(new DataItem("1", "A", true, null))), false));
        }

        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, questions);
        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).build())
                .questions(questions)
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(100));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("37. Bulk save - duplicate section titles → 200")
    void bulkSave_duplicateTitles_success() throws Exception {
        SectionWithQuestionsDto dto1 = createSampleSectionWithQuestions("Same", 1);
        SectionWithQuestionsDto dto2 = createSampleSectionWithQuestions("Same", 2);
        List<SectionWithQuestionsDto> request = List.of(dto1, dto2);

        List<SectionWithQuestionsDto> response = List.of(
                SectionWithQuestionsDto.builder().section(SectionDto.builder().id(201L).sectionTitle("Same").build()).build(),
                SectionWithQuestionsDto.builder().section(SectionDto.builder().id(202L).sectionTitle("Same").build()).build()
        );

        when(sectionService.saveSectionList(eq(CHALLENGE_ID), anyList())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/bulk-save/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].section.sectionTitle").value("Same"))
                .andExpect(jsonPath("$.data[1].section.sectionTitle").value("Same"));

        verify(sectionService, times(1)).saveSectionList(eq(CHALLENGE_ID), anyList());
    }

    @Test
    @DisplayName("38. Get section - deleted section → 404")
    void getSection_deleted_404() throws Exception {
        when(sectionService.getSection(SECTION_ID))
                .thenThrow(new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/v1/sections/{id}", SECTION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SECTION.NOT_FOUND));

        verify(sectionService, times(1)).getSection(SECTION_ID);
    }

    @Test
    @DisplayName("39. Bulk order - duplicate IDs → 400")
    void bulkOrder_duplicateIds_400() throws Exception {
        List<QuickBulkSectionRequest> req = List.of(
                new QuickBulkSectionRequest(201L, 1, false),
                new QuickBulkSectionRequest(201L, 2, false)
        );

        doThrow(new ApiException("Duplicate section IDs found", HttpStatus.BAD_REQUEST.value()))
                .when(sectionService).bulkOrderSection(eq(CHALLENGE_ID), anyList());

        mockMvc.perform(post("/api/v1/sections/bulk/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Duplicate section IDs found"));

        verify(sectionService, times(1)).bulkOrderSection(eq(CHALLENGE_ID), anyList());
    }

    @Test
    @DisplayName("40. List sections - very large page number → 200 empty")
    void listSections_largePageNumber() throws Exception {
        DataResponse<List<SectionWithQuestionsDto>> response = DataResponse.<List<SectionWithQuestionsDto>>builder()
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(List.of())
                .page(999).size(10).totalElements(0L).totalPages(0)
                .build();

        when(sectionService.listSections(eq(CHALLENGE_ID), eq(999), eq(10), isNull())).thenReturn(response);

        mockMvc.perform(get("/api/v1/sections/challenge/{challengeId}", CHALLENGE_ID)
                        .param("page", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.page").value(999));

        verify(sectionService, times(1)).listSections(eq(CHALLENGE_ID), eq(999), eq(10), isNull());
    }

    @Test
    @DisplayName("41. Save section - section with null title → 200")
    void saveSection_nullTitle_success() throws Exception {
        SectionDto sectionDto = new SectionDto(null, null, null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).sectionTitle(null).build())
                .questions(List.of(q))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section.sectionTitle").doesNotExist());

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("42. Bulk order - count mismatch → 400")
    void bulkOrder_countMismatch_400() throws Exception {
        List<QuickBulkSectionRequest> req = List.of(
                new QuickBulkSectionRequest(201L, 1, false)
        );

        doThrow(new ApiException(String.format(Const.SECTION.NON_DELETED_SECTIONS_COUNT_MISMATCH, 2, 1),
                HttpStatus.BAD_REQUEST.value()))
                .when(sectionService).bulkOrderSection(eq(CHALLENGE_ID), anyList());

        mockMvc.perform(post("/api/v1/sections/bulk/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(sectionService, times(1)).bulkOrderSection(eq(CHALLENGE_ID), anyList());
    }

    @Test
    @DisplayName("43. Save section - question with zero weight → 200")
    void saveSection_questionZeroWeight_success() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Test", null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 0.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).build())
                .questions(List.of(q))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].weight").value(0.0));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("44. List sections - with special characters in text filter → 200")
    void listSections_specialCharsInFilter() throws Exception {
        String specialText = "Math & Physics [2024]";

        DataResponse<List<SectionWithQuestionsDto>> response = DataResponse.<List<SectionWithQuestionsDto>>builder()
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(List.of())
                .page(0).size(10).totalElements(0L).totalPages(0)
                .build();

        when(sectionService.listSections(eq(CHALLENGE_ID), eq(0), eq(10), eq(specialText))).thenReturn(response);

        mockMvc.perform(get("/api/v1/sections/challenge/{challengeId}", CHALLENGE_ID)
                        .param("text", specialText))
                .andExpect(status().isOk());

        verify(sectionService, times(1)).listSections(eq(CHALLENGE_ID), eq(0), eq(10), eq(specialText));
    }

    @Test
    @DisplayName("45. Bulk order - all sections deleted → 200")
    void bulkOrder_allDeleted_success() throws Exception {
        List<QuickBulkSectionRequest> req = List.of(
                new QuickBulkSectionRequest(201L, null, true),
                new QuickBulkSectionRequest(202L, null, true)
        );

        doNothing().when(sectionService).bulkOrderSection(eq(CHALLENGE_ID), anyList());

        mockMvc.perform(post("/api/v1/sections/bulk/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sectionService, times(1)).bulkOrderSection(eq(CHALLENGE_ID), anyList());
    }

    // ====================== ADDITIONAL SAVE SECTION TEST CASES ======================

    @Test
    @DisplayName("46. Save section - invalid enum value for resourceType → 400")
    void saveSection_invalidEnumResourceType_400() throws Exception {
        String json = """
            {
              "section": { "sectionTitle": "Test", "resourceType": "INVALID_TYPE", "orderNumber": 1 },
              "questions": [ { "questionText": "Q1", "orderNumber": 1, "questionType": "MCQ", "weight": 1.0, "content": {"data": []} } ]
            }
            """;

        doThrow(new ApiException("Invalid enum value: INVALID_TYPE for ResourceType", HttpStatus.BAD_REQUEST.value()))
                .when(sectionService).saveSection(eq(CHALLENGE_ID), any());

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid enum value: INVALID_TYPE for ResourceType"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("47. Save section - section with video resourceType → 200")
    void saveSection_videoResourceType_success() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Video Section", "https://example.com/video", "Content", 1, "VIDEO");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).resourceType("VIDEO").build())
                .questions(List.of(q))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section.resourceType").value("VIDEO"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("48. Save section - section with image resourceType → 200")
    void saveSection_imageResourceType_success() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Image Section", "https://example.com/image.png", "Content", 1, "IMAGE");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).resourceType("IMAGE").build())
                .questions(List.of(q))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section.resourceType").value("IMAGE"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("49. Save section - multiple questions with different types → 200")
    void saveSection_multipleQuestionTypes_success() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Mixed", null, "Content", 1, "TEXT");

        List<QuestionDto> questions = List.of(
                new QuestionDto(null, "MCQ Question", 1, 1.0, "MCQ",
                        new DataContent(List.of(new DataItem("1", "A", true, null))), false),
                new QuestionDto(null, "TRUE_FALSE Question", 2, 1.0, "TRUE_FALSE",
                        new DataContent(List.of(new DataItem("1", "True", true, null))), false),
                new QuestionDto(null, "ESSAY Question", 3, 2.0, "ESSAY",
                        new DataContent(List.of()), false)
        );

        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, questions);
        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).build())
                .questions(questions)
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(3));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("50. Save section - question with negative weight → 400")
    void saveSection_negativeWeight_400() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Test", null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "Q1", 1, -1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException("Question weight must be non-negative", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Question weight must be non-negative"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("51. Save section - question with null orderNumber → 400")
    void saveSection_nullQuestionOrder_400() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Test", null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "Q1", 0, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException("Question order number is required", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Question order number is required"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("52. Save section - section with empty sectionsContent → 200")
    void saveSection_emptySectionsContent_success() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Test", null, "", 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).sectionsContent("").build())
                .questions(List.of(q))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section.sectionsContent").value(""));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("53. Save section - section with null sectionsContent → 200")
    void saveSection_nullSectionsContent_success() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Test", null, null, 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).sectionsContent(null).build())
                .questions(List.of(q))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("54. Save section - update existing section, change resourceType → 200")
    void saveSection_updateResourceType_success() throws Exception {
        SectionDto sectionDto = new SectionDto(SECTION_ID, "Updated", "https://example.com/video", "Content", 1, "VIDEO");
        QuestionDto q = new QuestionDto(300L, "Q1", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).resourceType("VIDEO").build())
                .questions(List.of(q))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section.resourceType").value("VIDEO"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("55. Save section - update existing, change orderNumber → 200")
    void saveSection_updateOrderNumber_success() throws Exception {
        SectionDto sectionDto = new SectionDto(SECTION_ID, "Test", null, "Content", 5, "TEXT");
        QuestionDto q = new QuestionDto(300L, "Q1", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).orderNumber(5).build())
                .questions(List.of(q))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section.orderNumber").value(5));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("56. Save section - section with null orderNumber, should use default → 200")
    void saveSection_nullOrderNumber_success() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Test", null, "Content", null, "TEXT");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).orderNumber(1).build())
                .questions(List.of(q))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section.orderNumber").exists());

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("57. Save section - MCQ question with no correct answer → 400")
    void saveSection_mcqNoCorrectAnswer_400() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Test", null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 1.0, "MCQ",
                new DataContent(List.of(
                        new DataItem("1", "A", false, null),
                        new DataItem("2", "B", false, null)
                )), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException("MCQ must have at least one correct answer", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MCQ must have at least one correct answer"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("58. Save section - question with null content → 400")
    void saveSection_nullQuestionContent_400() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Test", null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 1.0, "MCQ", null, false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException("Question content is required", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Question content is required"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("59. Save section - question with null questionType → 400")
    void saveSection_nullQuestionType_400() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Test", null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 1.0, null,
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException("Question type is required", HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Question type is required"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("60. Save section - update non-existent section ID → 404")
    void saveSection_updateNonExistentSection_404() throws Exception {
        SectionDto sectionDto = new SectionDto(999L, "Test", null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SECTION.NOT_FOUND));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("61. Save section - with sectionsUrl → 200")
    void saveSection_withSectionsUrl_success() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Test", "https://example.com/resource", "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder()
                        .id(SECTION_ID)
                        .sectionsUrl("https://example.com/resource")
                        .build())
                .questions(List.of(q))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section.sectionsUrl").value("https://example.com/resource"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("62. Save section - very long section title → 200")
    void saveSection_longTitle_success() throws Exception {
        String longTitle = "A".repeat(500);
        SectionDto sectionDto = new SectionDto(null, longTitle, null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).sectionTitle(longTitle).build())
                .questions(List.of(q))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section.sectionTitle").value(longTitle));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("63. Save section - question with very high weight → 200")
    void saveSection_highWeight_success() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Test", null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "Q1", 1, 999.99, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        SectionWithQuestionsDto response = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(SECTION_ID).build())
                .questions(List.of(q))
                .build();

        when(sectionService.saveSection(eq(CHALLENGE_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].weight").value(999.99));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("64. Save section - concurrent modification → 409")
    void saveSection_concurrentModification_409() throws Exception {
        SectionDto sectionDto = new SectionDto(SECTION_ID, "Test", null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(300L, "Q1", 1, 1.0, "MCQ",
                new DataContent(List.of(new DataItem("1", "A", true, null))), false);
        SectionWithQuestionsDto request = new SectionWithQuestionsDto(sectionDto, List.of(q));

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException("Concurrent modification detected", HttpStatus.CONFLICT.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Concurrent modification detected"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    @Test
    @DisplayName("65. Save section - unauthorized user → 401")
    void saveSection_unauthorized_401() throws Exception {
        SectionWithQuestionsDto request = createSampleSectionWithQuestions("Test", 1);

        when(sectionService.saveSection(eq(CHALLENGE_ID), any()))
                .thenThrow(new ApiException("Unauthorized", HttpStatus.UNAUTHORIZED.value()));

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verify(sectionService, times(1)).saveSection(eq(CHALLENGE_ID), any());
    }

    // ====================== HELPER METHOD ======================

    private SectionWithQuestionsDto createSampleSectionWithQuestions(String title, int order) {
        SectionDto section = new SectionDto(null, title, null, "Content", order, "TEXT");
        QuestionDto q = new QuestionDto(null, "Question?", 1, 1.0, "MCQ", new DataContent(List.of(
                new DataItem("a", "A", true, null)
        )), false);
        return new SectionWithQuestionsDto(section, List.of(q));
    }
}