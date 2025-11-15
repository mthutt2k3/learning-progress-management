package com.learning.progress.service;

import com.learning.progress.cache.CacheService;
import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.common.Const;
import com.learning.progress.common.ResourceType;
import com.learning.progress.dto.challenge.section.*;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.job.QuartzJobTriggerService;
import com.learning.progress.mapper.ChallengeSectionMapper;
import com.learning.progress.repository.*;
import com.learning.progress.service.QuestionService;
import com.learning.progress.service.impl.ChallengeSectionServiceImpl;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChallengeSectionServiceImplTest {

    @Mock private ChallengeSectionRepository sectionRepository;
    @Mock private DailyChallengeRepository challengeRepository;
    @Mock private QuestionService questionService;
    @Mock private ChallengeSectionMapper challengeSectionMapper;
    @Mock private AppValidator appValidator;
    @Mock private JwtUtil jwtUtil;
    @Mock private Validator validator;
    @Mock private CacheService cacheService;
    @Mock private SubmissionDailyChallengeRepository submissionRepo;
    @Mock private QuartzJobTriggerService quartzJobTriggerService;

    @InjectMocks
    private ChallengeSectionServiceImpl sectionService;

    private DailyChallenge draftChallenge;
    private DailyChallenge publishedChallenge;
    private final Long CHALLENGE_ID = 100L;
    private final Long CLASS_ID = 10L;

    @BeforeEach
    void setUp() {
        // Setup mock JWT
//        when(jwtUtil.extractEmailPrefixFromCurrentRequest()).thenReturn("teacher@school.com");

        // Setup class hierarchy
        Clazz clazz = Clazz.builder().id(CLASS_ID).build();
        ClassChapter chapter = ClassChapter.builder().clazz(clazz).build();
        ClassLesson lesson = ClassLesson.builder().classChapter(chapter).build();

        draftChallenge = DailyChallenge.builder()
                .id(CHALLENGE_ID)
                .classLesson(lesson)
                .challengeStatus(ChallengeStatus.DRAFT)
                .build();

        publishedChallenge = DailyChallenge.builder()
                .id(CHALLENGE_ID)
                .classLesson(lesson)
                .challengeStatus(ChallengeStatus.PUBLISHED)
                .build();
    }

    // =====================================================================
    // 1. CREATE NEW SECTION - DRAFT CHALLENGE → SUCCESS
    // =====================================================================
    @Test
    @DisplayName("1. Create new section - DRAFT challenge → 200")
    void saveSection_createNew_draft_success() {
        // Given
        SectionDto sectionDto = SectionDto.builder()
                .sectionTitle("Part 1")
                .resourceType("TEXT")
                .orderNumber(1)
                .build();

        QuestionDto q = QuestionDto.builder()
                .questionText("2+2=?")
                .orderNumber(1)
                .questionType("MCQ")
                .weight(1.0)
                .content(new DataContent(List.of(new DataItem("1", "4", true, null))))
                .build();

        SectionWithQuestionsDto dto = new SectionWithQuestionsDto(sectionDto, List.of(q));

        ChallengeSection savedSection = ChallengeSection.builder()
                .id(200L)
                .challenge(draftChallenge)
                .sectionTitle("Part 1")
                .resourceType(ResourceType.NONE)
                .orderNumber(1)
                .build();

        when(challengeRepository.findByIdAndDeletedAtIsNull(CHALLENGE_ID)).thenReturn(Optional.of(draftChallenge));
        doNothing().when(appValidator).validateUserAccessToClass(CLASS_ID);
        doNothing().when(appValidator).validateEnumValue(eq(ResourceType.class), anyString());

        when(challengeSectionMapper.toChallengeSectionEntity(sectionDto, draftChallenge)).thenReturn(savedSection);
        when(sectionRepository.save(any())).thenReturn(savedSection);

        QuestionDto savedQ = QuestionDto.builder().id(300L).questionText("2+2=?").build();
        when(questionService.bulkQuestion(List.of(q), 200L)).thenReturn(List.of(savedQ));

        SectionWithQuestionsDto expectedResponse = SectionWithQuestionsDto.builder()
                .section(SectionDto.builder().id(200L).sectionTitle("Part 1").build())
                .questions(List.of(savedQ))
                .build();
        when(challengeSectionMapper.toSectionWithQuestionsDto(savedSection, List.of(savedQ)))
                .thenReturn(expectedResponse);

        // When
        SectionWithQuestionsDto result = sectionService.saveSection(CHALLENGE_ID, dto);

        // Then
        assertNotNull(result);
        assertEquals(200L, result.getSection().getId());
        assertEquals(1, result.getQuestions().size());

        verify(challengeRepository).findByIdAndDeletedAtIsNull(CHALLENGE_ID);
        verify(appValidator).validateUserAccessToClass(CLASS_ID);
        verify(sectionRepository).save(any());
        verify(questionService).bulkQuestion(anyList(), eq(200L));
        verify(cacheService).clearCacheForChallenge(CHALLENGE_ID);
        verify(quartzJobTriggerService, never()).triggerAutoGrade(anyLong());
    }

    // =====================================================================
    // 2. UPDATE SECTION - DRAFT → SUCCESS
    // =====================================================================
    @Test
    @DisplayName("2. Update section - DRAFT → 200")
    void saveSection_update_draft_success() {
        SectionDto sectionDto = SectionDto.builder()
                .id(200L)
                .sectionTitle("Updated Title")
                .resourceType("NONE")
                .build();

        QuestionDto q = QuestionDto.builder().id(300L).questionText("Updated?").build();
        SectionWithQuestionsDto dto = new SectionWithQuestionsDto(sectionDto, List.of(q));

        ChallengeSection existing = ChallengeSection.builder().id(200L).challenge(draftChallenge).build();

        when(challengeRepository.findByIdAndDeletedAtIsNull(CHALLENGE_ID)).thenReturn(Optional.of(draftChallenge));
        when(sectionRepository.findByIdAndDeletedAtIsNull(200L)).thenReturn(Optional.of(existing));
        when(sectionRepository.save(any())).thenReturn(existing);

        QuestionDto updatedQ = QuestionDto.builder().id(300L).questionText("Updated?").build();
        when(questionService.bulkQuestion(anyList(), eq(200L))).thenReturn(List.of(updatedQ));

        when(challengeSectionMapper.toSectionWithQuestionsDto(any(), anyList()))
                .thenReturn(dto);

        // When
        SectionWithQuestionsDto result = sectionService.saveSection(CHALLENGE_ID, dto);

        // Then
        assertEquals("Updated Title", result.getSection().getSectionTitle());
        verify(sectionRepository).save(existing);
    }

    // =====================================================================
    // 3. CREATE NEW SECTION - PUBLISHED → 400
    // =====================================================================
    @Test
    @DisplayName("3. Create new section when PUBLISHED → 400")
    void saveSection_createNew_published_forbidden() {
        SectionDto sectionDto = SectionDto.builder().sectionTitle("New").build();
        SectionWithQuestionsDto dto = new SectionWithQuestionsDto(sectionDto, List.of(
                QuestionDto.builder().questionText("Q").orderNumber(1).questionType("MCQ").build()
        ));

        when(challengeRepository.findByIdAndDeletedAtIsNull(CHALLENGE_ID))
                .thenReturn(Optional.of(publishedChallenge));

        // When & Then
        ApiException ex = assertThrows(ApiException.class,
                () -> sectionService.saveSection(CHALLENGE_ID, dto));

        assertEquals("Cannot create a new section for a published challenge.", ex.getMessage());
        assertEquals(400, ex.getStatus());

        verify(sectionRepository, never()).save(any());
    }

    // =====================================================================
    // 4. UPDATE SECTION - PUBLISHED → SUCCESS
    // =====================================================================
    @Test
    @DisplayName("4. Update section when PUBLISHED → 200")
    void saveSection_update_published_success() {
        SectionDto sectionDto = SectionDto.builder().id(200L).sectionTitle("Updated").resourceType("NONE").build();
        QuestionDto q = QuestionDto.builder().id(300L).questionText("Updated").build();
        SectionWithQuestionsDto dto = new SectionWithQuestionsDto(sectionDto, List.of(q));

        ChallengeSection section = ChallengeSection.builder().id(200L).challenge(publishedChallenge).build();

        when(challengeRepository.findByIdAndDeletedAtIsNull(CHALLENGE_ID))
                .thenReturn(Optional.of(publishedChallenge));
        when(sectionRepository.findByIdAndDeletedAtIsNull(200L)).thenReturn(Optional.of(section));
        when(sectionRepository.save(any())).thenReturn(section);

        when(questionService.bulkQuestion(anyList(), eq(200L))).thenReturn(List.of(q));
        when(questionService.hasUpdates(anyList(), eq(200L))).thenReturn(true);

        SubmissionDailyChallenge sub = SubmissionDailyChallenge.builder().id(500L).build();
        when(submissionRepo.findIdsByChallengeIdAndDeletedAtIsNull(CHALLENGE_ID))
                .thenReturn(List.of(500L));

        when(challengeSectionMapper.toSectionWithQuestionsDto(any(), anyList()))
                .thenReturn(dto);

        // When
        SectionWithQuestionsDto result = sectionService.saveSection(CHALLENGE_ID, dto);

        // Then
        assertNotNull(result);
        verify(quartzJobTriggerService).triggerAutoGrade(500L);
        verify(cacheService).clearCacheForChallenge(CHALLENGE_ID);
    }

    // =====================================================================
    // 5. QUESTIONS UPDATED → TRIGGER AUTO-GRADE
    // =====================================================================
    @Test
    @DisplayName("5. Questions updated → trigger auto-grade")
    void saveSection_questionsUpdated_triggerAutoGrade() {
        // Given
        SectionDto sectionDto = SectionDto.builder()
                .id(200L)
                .sectionTitle("Updated")
                .resourceType("NONE")
                .build();

        QuestionDto q = QuestionDto.builder()
                .id(300L)
                .questionText("New Text")
                .build();

        SectionWithQuestionsDto dto = new SectionWithQuestionsDto(sectionDto, List.of(q));

        ChallengeSection existingSection = ChallengeSection.builder()
                .id(200L)
                .challenge(publishedChallenge)
                .sectionTitle("Old Title")
                .resourceType(ResourceType.NONE)
                .build();

        ChallengeSection savedSection = ChallengeSection.builder()
                .id(200L)
                .challenge(publishedChallenge)
                .sectionTitle("Updated")
                .resourceType(ResourceType.NONE)
                .build();

        // Mock DB
        when(challengeRepository.findByIdAndDeletedAtIsNull(CHALLENGE_ID))
                .thenReturn(Optional.of(publishedChallenge));

        when(sectionRepository.findByIdAndDeletedAtIsNull(200L))
                .thenReturn(Optional.of(existingSection));

        // QUAN TRỌNG: save() trả về savedSection (có thể giống existing)
        when(sectionRepository.save(any(ChallengeSection.class)))
                .thenAnswer(invocation -> {
                    ChallengeSection s = invocation.getArgument(0);
                    s.setId(200L); // đảm bảo ID không null
                    return s;
                });

        // questionService trả về đúng
        when(questionService.bulkQuestion(List.of(q), 200L))
                .thenReturn(List.of(q));

        when(questionService.hasUpdates(List.of(q), 200L))
                .thenReturn(true);

        when(submissionRepo.findIdsByChallengeIdAndDeletedAtIsNull(CHALLENGE_ID))
                .thenReturn(List.of(500L));

        // mapper trả về DTO
        when(challengeSectionMapper.toSectionWithQuestionsDto(any(), anyList()))
                .thenReturn(dto);

        // When
        sectionService.saveSection(CHALLENGE_ID, dto);

        // Then
        verify(quartzJobTriggerService).triggerAutoGrade(500L);
        verify(cacheService).clearCacheForChallenge(CHALLENGE_ID);
    }

    // =====================================================================
    // 6. QUESTIONS NOT UPDATED → SKIP AUTO-GRADE
    // =====================================================================
    @Test
    @DisplayName("6. Questions not updated → skip auto-grade")
    void saveSection_questionsNotUpdated_skipAutoGrade() {
        // Given
        SectionDto sectionDto = SectionDto.builder()
                .id(200L)
                .sectionTitle("Same Title")
                .resourceType("NONE")
                .build();

        QuestionDto q = QuestionDto.builder()
                .id(300L)
                .questionText("Same")
                .build();

        SectionWithQuestionsDto dto = new SectionWithQuestionsDto(sectionDto, List.of(q));

        ChallengeSection existingSection = ChallengeSection.builder()
                .id(200L)
                .challenge(publishedChallenge)
                .sectionTitle("Same Title")
                .resourceType(ResourceType.NONE)
                .build();

        // STUB ĐỦ:
        when(challengeRepository.findByIdAndDeletedAtIsNull(CHALLENGE_ID))
                .thenReturn(Optional.of(publishedChallenge));

        // QUAN TRỌNG: findSectionById(200L)
        when(sectionRepository.findByIdAndDeletedAtIsNull(200L))
                .thenReturn(Optional.of(existingSection));

        // save() trả về object đã update
        when(sectionRepository.save(any(ChallengeSection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // questionService
        when(questionService.bulkQuestion(eq(List.of(q)), eq(200L)))
                .thenReturn(List.of(q));

        when(questionService.hasUpdates(eq(List.of(q)), eq(200L)))
                .thenReturn(false);

        // mapper
        when(challengeSectionMapper.toSectionWithQuestionsDto(any(), anyList()))
                .thenReturn(dto);

        // When
        sectionService.saveSection(CHALLENGE_ID, dto);

        // Then
        verify(quartzJobTriggerService, never()).triggerAutoGrade(anyLong());
        verify(cacheService).clearCacheForChallenge(CHALLENGE_ID);
    }

    // =====================================================================
    // 7. NO QUESTIONS → 400
    // =====================================================================
    @Test
    @DisplayName("7. No questions → 400")
    void saveSection_noQuestions_400() {
        SectionDto sectionDto = SectionDto.builder()
                .sectionTitle("Test")
                .resourceType("TEXT")  // BẮT BUỘC: tránh NPE
                .build();

        SectionWithQuestionsDto dto = new SectionWithQuestionsDto(sectionDto, List.of());

        // XÓA DÒNG NÀY → không cần thiết
        // when(challengeRepository.findByIdAndDeletedAtIsNull(CHALLENGE_ID)).thenReturn(Optional.of(draftChallenge));

        ApiException ex = assertThrows(ApiException.class,
                () -> sectionService.saveSection(CHALLENGE_ID, dto));

        // SỬA MESSAGE CHO ĐÚNG VỚI PRODUCTION
        assertEquals("At least one question is required", ex.getMessage());
        assertEquals(400, ex.getStatus());
    }

    // =====================================================================
    // 8. NULL SECTION → 400
    // =====================================================================
    @Test
    @DisplayName("8. Null section → 400")
    void saveSection_nullSection_400() {
        SectionWithQuestionsDto dto = new SectionWithQuestionsDto(
                null,
                List.of(QuestionDto.builder()
                        .questionText("Q")
                        .orderNumber(1)
                        .questionType("MCQ")
                        .weight(1.0)
                        .content(new DataContent(List.of(new DataItem("1", "A", true, null))))
                        .build())
        );

        // Không cần stub DB

        ApiException ex = assertThrows(ApiException.class,
                () -> sectionService.saveSection(CHALLENGE_ID, dto));

        // SỬA MESSAGE CHO ĐÚNG
        assertEquals("Section is required", ex.getMessage());
        assertEquals(400, ex.getStatus());
    }

    // =====================================================================
    // 9. CHALLENGE NOT FOUND → 404
    // =====================================================================
    @Test
    @DisplayName("9. Challenge not found → 404")
    void saveSection_challengeNotFound_404() {
        SectionWithQuestionsDto dto = new SectionWithQuestionsDto(
                SectionDto.builder().build(),
                List.of(QuestionDto.builder().questionText("Q").orderNumber(1).questionType("MCQ").build())
        );

        when(challengeRepository.findByIdAndDeletedAtIsNull(CHALLENGE_ID)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> sectionService.saveSection(CHALLENGE_ID, dto));

        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getStatus());
    }

    // =====================================================================
    // 10. NO ACCESS TO CLASS → 403
    // =====================================================================
    @Test
    @DisplayName("10. No access to class → 403")
    void saveSection_noClassAccess_403() {
        SectionWithQuestionsDto dto = new SectionWithQuestionsDto(
                SectionDto.builder().build(),
                List.of(QuestionDto.builder().questionText("Q").orderNumber(1).questionType("MCQ").build())
        );

        when(challengeRepository.findByIdAndDeletedAtIsNull(CHALLENGE_ID)).thenReturn(Optional.of(draftChallenge));
        doThrow(new ApiException("Forbidden", 403)).when(appValidator).validateUserAccessToClass(CLASS_ID);

        ApiException ex = assertThrows(ApiException.class,
                () -> sectionService.saveSection(CHALLENGE_ID, dto));

        assertEquals(403, ex.getStatus());
    }
}