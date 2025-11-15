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

import static org.mockito.ArgumentMatchers.*;
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
    @DisplayName("2. Save section - empty questions → 400")
    void saveSection_emptyQuestions() throws Exception {
        String json = """
                { "section": { "sectionTitle": "Part 1", "resourceType": "TEXT" }, "questions": [] }
                """;

        mockMvc.perform(post("/api/v1/sections/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.SECTION.QUESTIONS_REQUIRED));

        verifyNoInteractions(sectionService);
    }

    @Test
    @DisplayName("3. Save section - success → 200")
    void saveSection_success() throws Exception {
        SectionDto sectionDto = new SectionDto(null, "Math Part 1", null, "Content", 1, "TEXT");
        QuestionDto q = new QuestionDto(null, "2+2=?", 1, 1.0, "MCQ", new DataContent(List.of(
                new DataItem("1", "4", true, null)
        )), false);

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

    // ====================== BULK SAVE (POST /bulk-save/{challengeId}) ======================

    @Test
    @DisplayName("4. Bulk save - success → 201")
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

    // ====================== BULK ORDER/DELETE (POST /bulk/{challengeId}) ======================

    @Test
    @DisplayName("5. Bulk order - invalid section ID → 400")
    void bulkOrder_invalidSectionId() throws Exception {
        QuickBulkSectionRequest req = new QuickBulkSectionRequest(999L, 1, false);

        // DÙNG doThrow() CHO METHOD VOID
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
    @DisplayName("6. Bulk delete - published challenge → 400")
    void bulkDelete_publishedChallenge() throws Exception {
        QuickBulkSectionRequest req = new QuickBulkSectionRequest(200L, null, true);

        // DÙNG doThrow() CHO METHOD VOID
        doThrow(new ApiException("Cannot delete sections for a published or higher challenge.", 400))
                .when(sectionService).bulkOrderSection(eq(CHALLENGE_ID), anyList());

        mockMvc.perform(post("/api/v1/sections/bulk/{challengeId}", CHALLENGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(req))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot delete sections for a published or higher challenge."));

        verify(sectionService, times(1)).bulkOrderSection(eq(CHALLENGE_ID), anyList());
    }

    @Test
    @DisplayName("7. Bulk order - success → 200")
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
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(sectionService, times(1)).bulkOrderSection(eq(CHALLENGE_ID), anyList());
    }

    // ====================== GET BY ID (GET /{id}) ======================

    @Test
    @DisplayName("8. Get section - not found → 404")
    void getSection_notFound() throws Exception {
        when(sectionService.getSection(999L))
                .thenThrow(new ApiException(Const.SECTION.NOT_FOUND, 404));

        mockMvc.perform(get("/api/v1/sections/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(Const.SECTION.NOT_FOUND));

        verify(sectionService, times(1)).getSection(999L);
    }

    @Test
    @DisplayName("9. Get section - success → 200")
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
    @DisplayName("10. List sections - invalid page → 400")
    void listSections_invalidPage() throws Exception {
        when(sectionService.listSections(eq(CHALLENGE_ID), eq(-1), eq(10), isNull()))
                .thenThrow(new ApiException("Page must be >= 0", 400));

        mockMvc.perform(get("/api/v1/sections/challenge/{challengeId}", CHALLENGE_ID)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page must be >= 0"));

        verify(sectionService, times(1)).listSections(eq(CHALLENGE_ID), eq(-1), eq(10), isNull());
    }

    @Test
    @DisplayName("11. List sections - success (cache miss) → 200")
    void listSections_success() throws Exception {
        SectionWithQuestionsDto dto = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(201L).sectionTitle("Sec1").build())
                .build();

        DataResponse<List<SectionWithQuestionsDto>> response = DataResponse.<List<SectionWithQuestionsDto>>builder()
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(List.of(dto))
                .page(0).size(10).totalElements(1L).totalPages(1)
                .build();

        when(sectionService.listSections(eq(CHALLENGE_ID), eq(0), eq(10), isNull())).thenReturn(response);

        mockMvc.perform(get("/api/v1/sections/challenge/{challengeId}", CHALLENGE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].section.id").value(201));

        verify(sectionService, times(1)).listSections(eq(CHALLENGE_ID), eq(0), eq(10), isNull());
    }

    // ====================== LIST PUBLIC SECTIONS (GET /challenge/{challengeId}/public) ======================

    @Test
    @DisplayName("12. List public sections - success → 200")
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
                .andExpect(jsonPath("$.data[0].questions[0].content").isEmpty()); // no answers

        verify(sectionService, times(1)).listSectionsWithoutAnswers(eq(CHALLENGE_ID), eq(0), eq(10), isNull());
    }

    // ====================== EXTRA FIELD IGNORED ======================

    @Test
    @DisplayName("13. Extra field in save section → ignored")
    void saveSection_extraFieldIgnored() throws Exception {
        String json = """
                {
                  "section": { "sectionTitle": "Test", "resourceType": "TEXT" },
                  "questions": [ { "questionText": "Q1", "orderNumber": 1, "questionType": "MCQ" } ],
                  "extra": "ignored"
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

    // ====================== HELPER METHOD ======================

    private SectionWithQuestionsDto createSampleSectionWithQuestions(String title, int order) {
        SectionDto section = new SectionDto(null, title, null, "Content", order, "TEXT");
        QuestionDto q = new QuestionDto(null, "Question?", 1, 1.0, "MCQ", new DataContent(List.of(
                new DataItem("a", "A", true, null)
        )), false);
        return new SectionWithQuestionsDto(section, List.of(q));
    }
}