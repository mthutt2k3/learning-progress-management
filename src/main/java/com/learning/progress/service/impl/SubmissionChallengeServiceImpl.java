package com.learning.progress.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learning.progress.common.ClassStudentStatus;
import com.learning.progress.common.Const;
import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.StudentChallengeListDTO;
import com.learning.progress.dto.challenge.section.DataContent;
import com.learning.progress.dto.submission.StudentSubmissionDTO;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.SubmissionMapper;
import com.learning.progress.repository.*;
import com.learning.progress.service.CacheService;
import com.learning.progress.service.SubmissionChallengeService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SubmissionChallengeServiceImpl implements SubmissionChallengeService {
    @Autowired
    private SubmissionDailyChallengeRepository submissionDailyChallengeRepository;

    @Autowired
    private ClassStudentRepository classStudentRepository;
    @Autowired
    private AppValidator appValidator;
    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private ClassLessonRepository classLessonRepository;
    @Autowired
    private SubmissionMapper submissionMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private SubmissionQuestionRepository submissionQuestionRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private DailyChallengeRepository dailyChallengeRepository;
    @Autowired
    private GradingDailyChallengeRepository gradingDailyChallengeRepository;
    @Autowired
    private CacheService cacheService;

    @Override
    @Async("taskExecutor")
    @Transactional
    public void createTemporarySubmissionsAsync(DailyChallenge challenge) {
        // Get class ID from ClassLesson -> ClassChapter -> Clazz
        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();

        // Use pagination to handle large classes
        int pageSize = 100;
        Pageable pageable = PageRequest.of(0, pageSize);
        Page<ClassStudent> studentPage;

        do {
            studentPage = classStudentRepository.findByClassIdAndStatus(
                    classId, List.of(ClassStudentStatus.ACTIVE), pageable);

            List<SubmissionDailyChallenge> submissions = new ArrayList<>();
            for (ClassStudent classStudent : studentPage.getContent()) {
                User student = classStudent.getUser();
                // Check if submission already exists
                if (submissionDailyChallengeRepository.findByUserIdAndChallengeIdAndDeletedAtIsNull(
                        student.getId(), challenge.getId()).isEmpty()) {
                    SubmissionDailyChallenge submission = new SubmissionDailyChallenge();
                    submission.setUser(student);
                    submission.setChallenge(challenge);
                    submission.setSubmissionStatus(SubmissionStatus.PENDING);
                    submission.setAutoSubmitted(false);
                    submission.setStartedAt(challenge.getStartDate());
                    submission.setExpiredAt(challenge.getEndDate());
                    submission.setPlagiarismScore(null);
                    submission.setSubmissionLogsJson(null);
                    submissions.add(submission);
                }
            }

            // Batch save submissions
            if (!submissions.isEmpty()) {
                submissionDailyChallengeRepository.saveAll(submissions);
            }

            pageable = pageable.next();
        } while (studentPage.hasNext());
    }

    public DataResponse<List<StudentChallengeListDTO>> getAllChallengesForStudent(Long classId, int page, int size, String text, String sortBy, String sortDir) {
        String traceId = TraceUtil.getTraceId();
        Long studentId = jwtUtil.extractUserIdFromCurrentRequest();
        log.info("[{}] Listing daily challenges for student {} in class {} with page: {}, size: {}, text: {}, sortBy: {}, sortDir: {}",
                traceId, studentId, classId, page, size, text, sortBy, sortDir);

        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "challengeName", "classLessonId"), sortBy, sortDir);
        log.debug("[{}] Pagination and sort parameters validated", traceId);
        appValidator.validateUserAccessToClass(classId);

        // Validate that the current user is the student or has permission
        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        if (!currentUserId.equals(studentId)) {
            throw new ApiException("Unauthorized: Cannot access challenges for another student", HttpStatus.FORBIDDEN.value());
        }

        // Create Sort and Pageable objects
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Validate class
        classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Fetch lessons + challenges
        Page<ClassLesson> lessonPage = classLessonRepository.findLessonsWithChallengesByClassId(
                classId, (text == null || text.isBlank()) ? "" : text, false, pageable);

        // Map to DTO
        List<StudentChallengeListDTO> data = lessonPage.getContent()
                .stream()
                .map(classLesson -> submissionMapper.toStudentChallengeListDTO(classLesson))
                .collect(Collectors.toList());

        log.info("[{}] Retrieved {} lessons ({} total)", traceId, data.size(), lessonPage.getTotalElements());
        return DataResponse.<List<StudentChallengeListDTO>>builder()
                .traceId(traceId)
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(data)
                .timestamp(LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(lessonPage.getTotalElements())
                .totalPages(lessonPage.getTotalPages())
                .build();
    }

    /**
     * @param challengeId
     * @param page
     * @param size
     * @param text
     * @param sortBy
     * @param sortDir
     * @return
     */
    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<StudentSubmissionDTO>> getSubmissionsByChallenge(Long challengeId, int page, int size, String text, String sortBy, String sortDir) {
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] Attempting to get submissions for challengeId: {}", traceId, challengeId);

        // Build cache key
        String cacheKey = cacheService.buildSubmissionsByChallengeCacheKey(
                challengeId, page, size, text, sortBy, sortDir);

        // Try cache first
        DataResponse<List<StudentSubmissionDTO>> cachedResult = cacheService.getCachedObject(
                cacheKey,
                new TypeReference<DataResponse<List<StudentSubmissionDTO>>>() {},
                traceId
        );

        if (cachedResult != null) {
            log.debug("[{}] Cache HIT for submissions: {}", traceId, cacheKey);
            return cachedResult;
        }
        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "submissionStatus", "studentName", "totalScore"), sortBy, sortDir);
        log.debug("[{}] Pagination and sort parameters validated", traceId);

        // Validate challenge
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> {
                    log.error("[{}] Challenge not found for challengeId: {}", traceId, challengeId);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Validate user access (teacher or teaching assistant)
        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);

        // Restrict access to teachers or teaching assistants
        String role = jwtUtil.extractRoleFromCurrentRequest();
        if (!role.equals("TEACHER") && !role.equals("TEACHING_ASSISTANT")) {
            log.error("[{}] Unauthorized access to submissions for challengeId: {} by user with role: {}", traceId, challengeId, role);
            throw new ApiException("Unauthorized: Only teachers or teaching assistants can view submissions", HttpStatus.FORBIDDEN.value());
        }

        // Create Sort and Pageable objects
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Fetch submissions with text filter
        Page<SubmissionDailyChallenge> submissionPage = submissionDailyChallengeRepository
                .findByChallengeIdAndDeletedAtIsNull(challengeId, text == null ? "" : text, pageable);

        // Fetch finalized gradings for the submissions
        List<Long> submissionIds = submissionPage.getContent().stream()
                .map(SubmissionDailyChallenge::getId)
                .collect(Collectors.toList());
        List<GradingDailyChallenge> gradings = submissionIds.isEmpty()
                ? new ArrayList<>()
                : gradingDailyChallengeRepository.findBySubmissionDailyIdInAndIsFinalizedTrueAndDeletedAtIsNull(submissionIds);
        Map<Long, Double> gradingScoreMap = gradings.stream()
                .collect(Collectors.toMap(
                        g -> g.getSubmissionDaily().getId(),
                        GradingDailyChallenge::getTotalScore,
                        (existing, replacement) -> existing // In case of duplicates, keep the first
                ));

        // Map to DTO
        List<StudentSubmissionDTO> data = submissionPage.getContent().stream()
                .map(submission -> {
                    StudentSubmissionDTO dto = new StudentSubmissionDTO();
                    dto.setSubmissionId(submission.getId());
                    dto.setStudentId(submission.getUser().getId());
                    dto.setStudentName(submission.getUser().getFullName() != null
                            ? submission.getUser().getFullName()
                            : submission.getUser().getEmail());
                    dto.setSubmissionStatus(submission.getSubmissionStatus());
                    dto.setSubmittedAt(submission.getSubmittedAt());
                    dto.setExpiredAt(submission.getExpiredAt());
                    dto.setAutoSubmitted(submission.getAutoSubmitted());
                    dto.setPlagiarismScore(submission.getPlagiarismScore());
                    // Set total score from grading or 0.0 if not graded
                    dto.setTotalScore(gradingScoreMap.getOrDefault(submission.getId(), null));
                    return dto;
                })
                .collect(Collectors.toList());

        log.info("[{}] Retrieved {} submissions ({} total) for challengeId: {}",
                traceId, data.size(), submissionPage.getTotalElements(), challengeId);

        DataResponse response =  DataResponse.<List<StudentSubmissionDTO>>builder()
                .traceId(traceId)
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(data)
                .timestamp(LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(submissionPage.getTotalElements())
                .totalPages(submissionPage.getTotalPages())
                .build();
        // Cache result
        cacheService.cacheObject(cacheKey, response, 5, traceId); // TTL 5 phút

        return response;
    }

    /**
     *
     */
    @Transactional
    public void autoSubmitExpiredSubmissions() {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Processing auto-submit for expired submissions", traceId);

        // Use pagination to handle large datasets
        int pageSize = 100;
        Pageable pageable = PageRequest.of(0, pageSize);
        Page<SubmissionDailyChallenge> submissionPage;

        do {
            // Find pending submissions that are expired and not auto-submitted
            submissionPage = submissionDailyChallengeRepository.findBySubmissionStatusAndAutoSubmittedFalseAndExpiredAtBefore(
                    SubmissionStatus.PENDING, OffsetDateTime.now(), pageable);

            List<SubmissionDailyChallenge> submissionsToUpdate = submissionPage.getContent();
            if (!submissionsToUpdate.isEmpty()) {
                // Update submissions to SUBMITTED and set autoSubmitted to true
                submissionsToUpdate.forEach(submission -> {
                    submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
                    submission.setAutoSubmitted(true);
                    submission.setSubmittedAt(OffsetDateTime.now());
                });

                // Batch save updates
                submissionDailyChallengeRepository.saveAll(submissionsToUpdate);
                log.debug("[{}] Auto-submitted {} submissions", traceId, submissionsToUpdate.size());
            }

            pageable = pageable.next();
        } while (submissionPage.hasNext());
    }
}
