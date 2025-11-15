package com.learning.progress.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learning.progress.cache.CacheService;
import com.learning.progress.common.*;
import com.learning.progress.dto.challenge.section.DataContent;
import com.learning.progress.dto.challenge.section.DataItem;
import com.learning.progress.dto.submission.*;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.job.QuartzJobTriggerService;
import com.learning.progress.mapper.ChallengeSectionMapper;
import com.learning.progress.repository.*;
import com.learning.progress.service.GradingDailyChallengeService;
import com.learning.progress.service.NotificationService;
import com.learning.progress.service.impl.SubmissionQuestionServiceImpl;
import com.learning.progress.service.validator.SubmissionQuestionValidator;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionQuestionServiceImplTest {

    @Mock private SubmissionDailyChallengeRepository submissionDailyChallengeRepository;
    @Mock private SubmissionQuestionRepository submissionQuestionRepository;
    @Mock private AppValidator appValidator;
    @Mock private JwtUtil jwtUtil;
    @Mock private SubmissionQuestionValidator submissionQuestionValidator;
    @Mock private QuestionRepository questionRepository;
    @Mock private GradingDailyChallengeService gradingDailyChallengeService;
    @Mock private CacheService cacheService;
    @Mock private QuartzJobTriggerService quartzJobTriggerService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private ChallengeSectionRepository challengeSectionRepository;
    @Mock private GradingQuestionRepository gradingQuestionRepository;
    @Mock private ChallengeSectionMapper challengeSectionMapper;
    @Mock private NotificationService notificationService;

    @InjectMocks private SubmissionQuestionServiceImpl service;

    private final Long USER_ID = 1L;
    private final Long CHALLENGE_ID = 100L;
    private final Long SUBMISSION_ID = 200L;
    private final Long QUESTION_ID = 300L;
    private final Long SUBMISSION_QUESTION_ID = 400L;

    @Test
    @DisplayName("1. getSubmissionResult → cache hit → return cached")
    void getSubmissionResult_cacheHit() {
        when(jwtUtil.extractUserIdFromCurrentRequest()).thenReturn(USER_ID);
        String cacheKey = "submission:result:user:1:submission:200";
        when(cacheService.buildSubmissionResultCacheKey(USER_ID, SUBMISSION_ID)).thenReturn(cacheKey);

        SubmissionResultResponse cached = new SubmissionResultResponse();
        when(cacheService.getCachedObject(eq(cacheKey), any(TypeReference.class))).thenReturn(cached);

        SubmissionResultResponse result = service.getSubmissionResult(SUBMISSION_ID);

        assertSame(cached, result);
        verify(cacheService, never()).cacheObject(any(), any(), anyLong());
    }

    @Test
    @DisplayName("2. getSubmissionResult → cache miss → build & cache")
    void getSubmissionResult_cacheMiss() {
        // GIVEN
        when(jwtUtil.extractUserIdFromCurrentRequest()).thenReturn(USER_ID);
        String cacheKey = "submission:result:user:1:submission:200";
        when(cacheService.buildSubmissionResultCacheKey(USER_ID, SUBMISSION_ID)).thenReturn(cacheKey);
        when(cacheService.getCachedObject(eq(cacheKey), any(TypeReference.class))).thenReturn(null);

        SubmissionDailyChallenge submission = SubmissionDailyChallenge.builder()
                .id(SUBMISSION_ID)
                .submissionStatus(SubmissionStatus.SUBMITTED)
                .challenge(DailyChallenge.builder().id(CHALLENGE_ID).build())
                .build();
        when(appValidator.validateUserAccessToSubmissionResult(SUBMISSION_ID)).thenReturn(submission);

        ChallengeSection section = ChallengeSection.builder()
                .id(10L)
                .sectionTitle("Section 1")
                .orderNumber(1)
                .resourceType(ResourceType.NONE)
                .questions(List.of(
                        Question.builder()
                                .id(QUESTION_ID)
                                .questionText("What is 2+2?")
                                .questionType(QuestionType.MULTIPLE_CHOICE)
                                .weight(10.0)
                                .orderNumber(1)  // THÊM DÒNG NÀY
                                .questionContentJson(JsonUtil.objectToMap(DataContent.builder().data(List.of()).build()))
                                .build()
                ))
                .build();

        when(challengeSectionRepository.findByChallengeIdAndDeletedAtIsNullOrderByOrderNumberAsc(CHALLENGE_ID))
                .thenReturn(List.of(section));

        SubmissionQuestion sq = SubmissionQuestion.builder()
                .id(SUBMISSION_QUESTION_ID)
                .question(Question.builder().id(QUESTION_ID).build())
                .submissionContentJson(JsonUtil.objectToMap(AnswerContent.builder().data(List.of()).build()))
                .build();

        when(submissionQuestionRepository.findBySubmissionDailyIdAndDeletedAtIsNull(SUBMISSION_ID))
                .thenReturn(List.of(sq));

        when(gradingQuestionRepository.findBySubmissionQuestion_SubmissionDaily_IdAndDeletedAtIsNull(SUBMISSION_ID))
                .thenReturn(List.of());

        // WHEN
        SubmissionResultResponse result = service.getSubmissionResult(SUBMISSION_ID);

        // THEN
        assertNotNull(result);
        assertEquals(CHALLENGE_ID, result.getChallengeId());
        assertEquals(1, result.getSectionDetails().size());
        assertEquals(1, result.getSectionDetails().get(0).getQuestionResults().size());
        assertEquals(1, result.getSectionDetails().get(0).getQuestionResults().get(0).getOrderNumber()); // Verify orderNumber

        verify(cacheService).cacheObject(eq(cacheKey), eq(result), eq(10L));
    }

    @Test
    @DisplayName("3. getDraftSubmission → PENDING → DRAFT + return draft")
    void getDraftSubmission_pendingToDraft() {
        SubmissionDailyChallenge submission = SubmissionDailyChallenge.builder()
                .id(SUBMISSION_ID)
                .submissionStatus(SubmissionStatus.PENDING)
                .challenge(DailyChallenge.builder().id(CHALLENGE_ID).build())
                .build();

        when(appValidator.validateUserAccessToSubmission(SUBMISSION_ID)).thenReturn(submission);
        when(challengeSectionRepository.findByChallengeIdAndDeletedAtIsNullOrderByOrderNumberAsc(CHALLENGE_ID))
                .thenReturn(List.of());
        when(submissionQuestionRepository.findBySubmissionDailyIdAndDeletedAtIsNull(SUBMISSION_ID))
                .thenReturn(List.of());

        DraftSubmissionResponse result = service.getDraftSubmission(SUBMISSION_ID);

        assertEquals(SubmissionStatus.DRAFT, submission.getSubmissionStatus());
        verify(submissionDailyChallengeRepository).save(submission);
        assertEquals(SubmissionStatus.DRAFT, result.getStatus());
    }

    @Test
    @DisplayName("4. saveSubmission → draft save → no submit")
    void saveSubmission_draft() {
        // GIVEN
        SubmissionDailyChallenge submission = SubmissionDailyChallenge.builder()
                .id(SUBMISSION_ID)
                .submissionStatus(SubmissionStatus.PENDING)
                .challenge(DailyChallenge.builder().id(CHALLENGE_ID).build())
                .build();

        doReturn(submission).when(appValidator).validateUserAccessToSubmission(SUBMISSION_ID);
        when(jwtUtil.extractUserIdFromCurrentRequest()).thenReturn(USER_ID);

        // THÊM 1 CÂU TRẢ LỜI ĐỂ toSave KHÔNG RỖNG
        AnswerContent answerContent = AnswerContent.builder()
                .data(List.of(AnswerItem.builder().id("opt2").value("Banana").build()))
                .build();

        SaveSubmissionRequest.QuestionAnswer questionAnswer = SaveSubmissionRequest.QuestionAnswer.builder()
                .questionId(QUESTION_ID)
                .content(answerContent)
                .build();

        SaveSubmissionRequest request = SaveSubmissionRequest.builder()
                .saveAsDraft(true)
                .questionAnswers(List.of(questionAnswer))
                .build();

        doNothing().when(submissionQuestionValidator).validateSubmissionQuestions(anyLong(), any());

        // MOCK question tồn tại
        Question question = Question.builder().id(QUESTION_ID).build();
        when(questionRepository.findAllByIdInAndDeletedAtIsNull(List.of(QUESTION_ID)))
                .thenReturn(List.of(question));

        // WHEN
        service.saveSubmission(SUBMISSION_ID, request);

        // THEN
        ArgumentCaptor<SubmissionDailyChallenge> captor = ArgumentCaptor.forClass(SubmissionDailyChallenge.class);
        verify(submissionDailyChallengeRepository).save(captor.capture());

        SubmissionDailyChallenge saved = captor.getValue();
        assertEquals(SubmissionStatus.DRAFT, saved.getSubmissionStatus());
        assertSame(submission, saved);

        verify(submissionQuestionRepository).saveAll(anyList());
        verify(quartzJobTriggerService, never()).triggerAutoGrade(anyLong());
        verify(cacheService).clearSubmissionCache(USER_ID, SUBMISSION_ID);
    }

    @Test
    @DisplayName("5. saveSubmission → submit → trigger grading + notify")
    void saveSubmission_submit() {
        SubmissionDailyChallenge submission = SubmissionDailyChallenge.builder()
                .id(SUBMISSION_ID)
                .submissionStatus(SubmissionStatus.DRAFT)
                .challenge(DailyChallenge.builder().id(CHALLENGE_ID).challengeType(ChallengeType.GV).build())
                .build();

        when(appValidator.validateUserAccessToSubmission(SUBMISSION_ID)).thenReturn(submission);
        when(jwtUtil.extractUserIdFromCurrentRequest()).thenReturn(USER_ID);

        SaveSubmissionRequest request = SaveSubmissionRequest.builder()
                .saveAsDraft(false)
                .questionAnswers(List.of())
                .build();

        doNothing().when(submissionQuestionValidator).validateSubmissionQuestions(anyLong(), any());

        service.saveSubmission(SUBMISSION_ID, request);

        verify(submissionDailyChallengeRepository).saveAndFlush(submission);
        verify(quartzJobTriggerService).triggerAutoGrade(SUBMISSION_ID);
        verify(notificationService).createNotification(eq(USER_ID), isNull(), anyString(), anyString(), isNull(), isNull());
        verify(cacheService).clearSubmissionsCacheForChallenge(CHALLENGE_ID);
    }

    @Test
    @DisplayName("6. getQuestionDetail → success")
    void getQuestionDetail_success() {
        // GIVEN
        SubmissionDailyChallenge submission = SubmissionDailyChallenge.builder()
                .id(SUBMISSION_ID)
                .submissionStatus(SubmissionStatus.SUBMITTED)
                .challenge(DailyChallenge.builder()
                        .id(CHALLENGE_ID)
                        .challengeStatus(ChallengeStatus.FINISHED)
                        .classLesson(ClassLesson.builder()
                                .classChapter(ClassChapter.builder()
                                        .clazz(Clazz.builder().id(100L).build())
                                        .build())
                                .build())
                        .build())
                .user(User.builder().id(USER_ID).build())
                .build();

        // === CÂU HỎI: 3 lựa chọn ===
        List<DataItem> questionData = List.of(
                DataItem.builder().id("opt1").value("Apple").isCorrect(false).positionId(null).build(),
                DataItem.builder().id("opt2").value("Banana").isCorrect(true).positionId(null).build(),
                DataItem.builder().id("opt3").value("Cherry").isCorrect(false).positionId(null).build()
        );

        // === CÂU TRẢ LỜI: chọn "Banana" ===
        List<AnswerItem> answerData = List.of(
                AnswerItem.builder().id("opt2").value("Banana").positionId(null).build()
        );

        SubmissionQuestion sq = SubmissionQuestion.builder()
                .id(SUBMISSION_QUESTION_ID)
                .submissionDaily(submission)
                .question(Question.builder()
                        .id(QUESTION_ID)
                        .questionText("What is the correct fruit?")
                        .questionType(QuestionType.MULTIPLE_CHOICE)
                        .weight(10.0)
                        .orderNumber(1)
                        .questionContentJson(JsonUtil.objectToMap(DataContent.builder().data(questionData).build()))
                        .build())
                .submissionContentJson(JsonUtil.objectToMap(AnswerContent.builder().data(answerData).build()))
                .build();

        when(submissionQuestionRepository.findById(SUBMISSION_QUESTION_ID)).thenReturn(Optional.of(sq));
        doReturn(submission).when(appValidator).validateUserAccessToSubmission(SUBMISSION_ID);
        when(gradingQuestionRepository.findBySubmissionQuestionIdAndDeletedAtIsNull(SUBMISSION_QUESTION_ID))
                .thenReturn(Optional.empty());

        // WHEN
        SubmissionResultResponse.QuestionResult result = service.getQuestionDetail(SUBMISSION_QUESTION_ID);

        // THEN
        assertNotNull(result);
        assertEquals(QUESTION_ID, result.getQuestionId());
        assertEquals("What is the correct fruit?", result.getQuestionText());
        assertEquals(QuestionType.MULTIPLE_CHOICE, result.getQuestionType());
        assertEquals(10.0, result.getScore(), 0.01);
        assertEquals(1, result.getOrderNumber());

        // === QUESTION CONTENT ===
        DataContent questionContent = result.getQuestionContent();
        assertNotNull(questionContent);
        assertEquals(3, questionContent.getData().size());
        DataItem opt2 = questionContent.getData().get(1);
        assertEquals("opt2", opt2.getId());
        assertEquals("Banana", opt2.getValue());
        assertTrue(opt2.isCorrect());

        // === SUBMITTED CONTENT ===
        AnswerContent submittedContent = result.getSubmittedContent();
        assertNotNull(submittedContent);
        assertEquals(1, submittedContent.getData().size());
        AnswerItem submitted = submittedContent.getData().get(0);
        assertEquals("opt2", submitted.getId());
        assertEquals("Banana", submitted.getValue());

        assertNull(result.getGradingQuestionResult());
    }
}