package com.learning.progress.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learning.progress.cache.CacheService;
import com.learning.progress.common.*;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.DailyChallengeListDTO;
import com.learning.progress.dto.challenge.StudentChallengeListDTO;
import com.learning.progress.dto.submission.StudentSubmissionDTO;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.DailyChallengeMapper;
import com.learning.progress.mapper.SubmissionMapper;
import com.learning.progress.repository.*;
import com.learning.progress.service.impl.SubmissionChallengeServiceImpl;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionChallengeServiceImplTest {

    @Mock private SubmissionDailyChallengeRepository submissionRepo;
    @Mock private DailyChallengeRepository challengeRepo;
    @Mock private ClassStudentRepository classStudentRepo;
    @Mock private GradingDailyChallengeRepository gradingRepo;
    @Mock private GradingQuestionRepository gradingQuestionRepo;
    @Mock private QuestionRepository questionRepo;
    @Mock private UserRepository userRepo;
    @Mock private AppValidator appValidator;
    @Mock private JwtUtil jwtUtil;
    @Mock private CacheService cacheService;
    @Mock private DailyChallengeMapper challengeMapper;
    @Mock private SubmissionMapper submissionMapper;
    @Mock private EntityManager entityManager;

    @InjectMocks private SubmissionChallengeServiceImpl service;

    private static final Long CHALLENGE_ID = 100L;
    private static final Long SUBMISSION_ID = 200L;
    private static final Long STUDENT_ID = 300L;
    private static final Long CLASS_ID = 10L;

    @Test
    @DisplayName("1. getAllChallengesForStudent → success")
    void getAllChallengesForStudent_success() {
        when(jwtUtil.extractUserIdFromCurrentRequest()).thenReturn(STUDENT_ID);
        doNothing().when(appValidator).validateUserAccessToClass(CLASS_ID);

        ClassLesson lesson = ClassLesson.builder().id(50L).classLessonName("Lesson 1").build();
        DailyChallenge challenge = DailyChallenge.builder()
                .id(CHALLENGE_ID)
                .classLesson(lesson)
                .challengeStatus(ChallengeStatus.PUBLISHED)
                .challengeName("Math Quiz")
                .startDate(OffsetDateTime.now().minusDays(1))
                .endDate(OffsetDateTime.now().plusDays(1))
                .build();

        Page<ClassLesson> lessonPage = new PageImpl<>(List.of(lesson));
        when(challengeRepo.findLessonsWithChallengesByClassId(eq(CLASS_ID), any(), eq(false), any(Pageable.class)))
                .thenReturn(lessonPage);

        when(challengeRepo.findByClassLessonIdInAndDeletedAtIsNull(List.of(50L)))
                .thenReturn(List.of(challenge));

        SubmissionDailyChallenge submission = SubmissionDailyChallenge.builder()
                .id(SUBMISSION_ID)
                .user(User.builder().id(STUDENT_ID).build())
                .challenge(challenge)
                .submissionStatus(SubmissionStatus.DRAFT)
                .actualStartAt(OffsetDateTime.now().minusMinutes(10))
                .build();

        when(submissionRepo.findByUserIdAndChallengeIdInAndDeletedAtIsNull(STUDENT_ID, List.of(CHALLENGE_ID)))
                .thenReturn(List.of(submission));

        when(gradingRepo.findBySubmissionDailyIdInAndDeletedAtIsNull(List.of(SUBMISSION_ID)))
                .thenReturn(List.of());

        when(questionRepo.getMaxWeightByChallengeIds(List.of(CHALLENGE_ID)))
                .thenReturn(Map.of(CHALLENGE_ID, 100.0));

        DailyChallengeListDTO.DailyChallengeInLessonDTO challengeDTO =
                DailyChallengeListDTO.DailyChallengeInLessonDTO.builder()
                        .id(CHALLENGE_ID)
                        .challengeName("Math Quiz")
                        .build();

        when(challengeMapper.dailyChallengeToDailyChallengeInLessonDTO(challenge))
                .thenReturn(challengeDTO);

        StudentSubmissionDTO submissionDTO = StudentSubmissionDTO.builder()
                .submissionId(SUBMISSION_ID)
                .submissionStatus(SubmissionStatus.DRAFT)
                .build();

        when(submissionMapper.toStudentSubmissionDTO(eq(submission), isNull(), isNull(), eq(100.0), isNull()))
                .thenReturn(submissionDTO);

        DataResponse<List<StudentChallengeListDTO>> response =
                service.getAllChallengesForStudent(CLASS_ID, 0, 10, null);

        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());
        assertEquals(SUBMISSION_ID, response.getData().get(0).getChallenges().get(0).getStudentSubmission().getSubmissionId());
    }

    @Test
    @DisplayName("2. getSubmissionsByChallenge → cache miss → success")
    void getSubmissionsByChallenge_cacheMiss_success() {
        // GIVEN
        when(jwtUtil.extractRoleFromCurrentRequest()).thenReturn("TEACHER");
        doNothing().when(appValidator).validateUserAccessToClass(CLASS_ID);

        DailyChallenge challenge = DailyChallenge.builder()
                .id(CHALLENGE_ID)
                .classLesson(ClassLesson.builder()
                        .classChapter(ClassChapter.builder()
                                .clazz(Clazz.builder().id(CLASS_ID).build())
                                .build())
                        .build())
                .build();

        when(challengeRepo.findByIdAndDeletedAtIsNull(CHALLENGE_ID)).thenReturn(Optional.of(challenge));

        SubmissionDailyChallenge sub = SubmissionDailyChallenge.builder()
                .id(SUBMISSION_ID)
                .user(User.builder().id(STUDENT_ID).fullName("Student A").build())
                .submissionStatus(SubmissionStatus.SUBMITTED)
                .build();

        Page<SubmissionDailyChallenge> page = new PageImpl<>(List.of(sub));
        when(submissionRepo.findByChallengeIdAndDeletedAtIsNull(eq(CHALLENGE_ID), eq(""), any(Pageable.class)))
                .thenReturn(page);

        when(gradingRepo.findBySubmissionDailyIdInAndDeletedAtIsNull(List.of(SUBMISSION_ID)))
                .thenReturn(List.of());

        when(questionRepo.findByChallengeIdAndDeletedAtIsNull(CHALLENGE_ID))
                .thenReturn(List.of(Question.builder().weight(100.0).build()));

        StudentSubmissionDTO dto = StudentSubmissionDTO.builder()
                .submissionId(SUBMISSION_ID)
                .studentName("Student A")
                .totalWeight(0.0)
                .maxPossibleWeight(100.0)
                .finalScore(0.0)
                .build();

        when(submissionMapper.toStudentSubmissionDTO(
                eq(sub),
                isNull(),
                isNull(),
                eq(100.0),
                isNull()
        )).thenReturn(dto);

        when(cacheService.getCachedObject(isNull(), any(TypeReference.class))).thenReturn(null);

        // WHEN
        DataResponse<List<StudentSubmissionDTO>> response =
                service.getSubmissionsByChallenge(CHALLENGE_ID, 0, 10, null, "createdAt", "desc");

        // THEN
        assertEquals(1, response.getData().size());

        // SỬA: key = null, ttl = 5L (Long)
        verify(cacheService).cacheObject(
                isNull(),           // key = null
                anyList(),          // list DTO
                eq(5L)              // ttl là Long
        );
    }

    @Test
    @DisplayName("3. startSubmission → PENDING → DRAFT")
    void startSubmission_success() {
        when(jwtUtil.extractUserIdFromCurrentRequest()).thenReturn(STUDENT_ID);

        SubmissionDailyChallenge submission = SubmissionDailyChallenge.builder()
                .id(SUBMISSION_ID)
                .user(User.builder().id(STUDENT_ID).build())
                .submissionStatus(SubmissionStatus.PENDING)
                .challenge(DailyChallenge.builder().id(CHALLENGE_ID).build())
                .build();

        when(submissionRepo.findByIdAndDeletedAtIsNull(SUBMISSION_ID))
                .thenReturn(Optional.of(submission));

        service.startSubmission(SUBMISSION_ID);

        assertEquals(SubmissionStatus.DRAFT, submission.getSubmissionStatus());
        assertNotNull(submission.getActualStartAt());
        verify(submissionRepo).save(submission);
        verify(cacheService).clearSubmissionCache(STUDENT_ID, SUBMISSION_ID);
        verify(cacheService).clearSubmissionsCacheForChallenge(CHALLENGE_ID);
    }

    @Test
    @DisplayName("4. getSubmissionInfo → with grading")
    void getSubmissionInfo_withGrading() {
        SubmissionDailyChallenge submission = SubmissionDailyChallenge.builder()
                .id(SUBMISSION_ID)
                .challenge(DailyChallenge.builder().id(CHALLENGE_ID).build())
                .build();

        when(appValidator.validateUserAccessToSubmission(SUBMISSION_ID)).thenReturn(submission);

        GradingDailyChallenge grading = GradingDailyChallenge.builder().id(400L).build();
        when(gradingRepo.findBySubmissionDailyIdAndDeletedAtIsNull(SUBMISSION_ID))
                .thenReturn(Optional.of(grading));

        when(gradingRepo.sumReceivedWeightBySubmissionDailyId(SUBMISSION_ID)).thenReturn(75.0);
        when(challengeRepo.sumQuestionWeightByChallengeId(CHALLENGE_ID)).thenReturn(BigDecimal.valueOf(100));

        StudentSubmissionDTO dto = StudentSubmissionDTO.builder().finalScore(75.0).build();
        when(submissionMapper.toStudentSubmissionDTO(any(), any(), any(), any(), any())).thenReturn(dto);

        StudentSubmissionDTO result = service.getSubmissionInfo(SUBMISSION_ID);
        assertEquals(75.0, result.getFinalScore());
    }
}