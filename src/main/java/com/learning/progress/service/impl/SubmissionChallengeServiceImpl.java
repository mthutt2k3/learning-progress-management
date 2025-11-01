package com.learning.progress.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learning.progress.common.*;
import org.json.JSONArray;
import org.json.JSONObject;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.StudentChallengeListDTO;
import com.learning.progress.dto.submission.StudentSubmissionDTO;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.SubmissionMapper;
import com.learning.progress.repository.*;
import com.learning.progress.cache.CacheService;
import com.learning.progress.service.SubmissionChallengeService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();

        int pageSize = 100;
        Pageable pageable = PageRequest.of(0, pageSize);
        Page<ClassStudent> studentPage;

        do {
            studentPage = classStudentRepository.findByClassIdAndStatus(
                    classId, List.of(ClassStudentStatus.ACTIVE), pageable);

            List<SubmissionDailyChallenge> submissions = new ArrayList<>();
            for (ClassStudent classStudent : studentPage.getContent()) {
                User student = classStudent.getUser();
                if (submissionDailyChallengeRepository.findByUserIdAndChallengeIdAndDeletedAtIsNull(
                        student.getId(), challenge.getId()).isEmpty()) {
                    SubmissionDailyChallenge submission = new SubmissionDailyChallenge();
                    submission.setUser(student);
                    submission.setChallenge(challenge);
                    submission.setSubmissionStatus(SubmissionStatus.PENDING);
                    submission.setAutoSubmitted(false);
                    submission.setStartedAt(challenge.getStartDate());
                    submission.setExpiredAt(challenge.getEndDate());
                    submissions.add(submission);
                }
            }

            if (!submissions.isEmpty()) {
                submissionDailyChallengeRepository.saveAll(submissions);
            }

            pageable = pageable.next();
        } while (studentPage.hasNext());
    }

    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<StudentChallengeListDTO>> getAllChallengesForStudent(
            Long classId, int page, int size, String text) {

        Long studentId = jwtUtil.extractUserIdFromCurrentRequest();
        appValidator.validatePaginationParams(page, size);
        appValidator.validateUserAccessToClass(classId);

        Pageable pageable = PageRequest.of(page, size);
        Page<Object[]> result = dailyChallengeRepository.findStudentChallengesNative(
                classId, studentId, text, (long) page * size, size, pageable
        );

        List<StudentChallengeListDTO> data = result.getContent().stream()
                .map(row -> {
                    Long lessonId = (Long) row[0];
                    String name = (String) row[1];
                    String content = (String) row[2];
                    Integer order = (Integer) row[3];
                    String challengesJson = (String) row[4];

                    List<StudentChallengeListDTO.StudentChallengeDTO> challenges = parseChallengesJson(challengesJson);
                    return new StudentChallengeListDTO(lessonId, name, content, order, challenges);
                })
                .toList();

        return DataResponse.success(data, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .page(page).size(size)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages());
    }

    private List<StudentChallengeListDTO.StudentChallengeDTO> parseChallengesJson(String json) {
        if (json == null || json.equals("[]")) return List.of();

        JSONArray array = new JSONArray(json);
        return IntStream.range(0, array.length())
                .mapToObj(i -> {
                    JSONObject obj = array.getJSONObject(i);
                    return new StudentChallengeListDTO.StudentChallengeDTO(
                            obj.getLong("id"),
                            obj.getString("challengeName"),
                            ChallengeType.valueOf(obj.getString("challengeType")),
                            ChallengeStatus.valueOf(obj.getString("challengeStatus")),
                            obj.isNull("submissionChallengeId") ? null : obj.getLong("submissionChallengeId"),
                            obj.isNull("startDate") ? null : OffsetDateTime.parse(obj.getString("startDate")),
                            obj.isNull("endDate") ? null : OffsetDateTime.parse(obj.getString("endDate")),
                            obj.isNull("submissionStatus") ? null : SubmissionStatus.valueOf(obj.getString("submissionStatus")),
                            obj.isNull("submittedAt") ? null : OffsetDateTime.parse(obj.getString("submittedAt")),
                            obj.isNull("totalScore") ? null : obj.getDouble("totalScore"),
                            obj.isNull("scorePercentage") ? null : obj.getDouble("scorePercentage")
                    );
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<StudentSubmissionDTO>> getSubmissionsByChallenge(Long challengeId, int page, int size,
                                                                              String text, String sortBy, String sortDir) {
        log.debug("Attempting to get submissions for challengeId: {}", challengeId);

        String cacheKey = cacheService.buildSubmissionsByChallengeCacheKey(challengeId, page, size, text, sortBy, sortDir);
        List<StudentSubmissionDTO> cachedData = cacheService.getCachedObject(cacheKey, new TypeReference<>() {
        });

        if (cachedData != null) {
            log.debug("Cache HIT for submissions: {}", cacheKey);
            return DataResponse.success(cachedData, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                    .page(page)
                    .size(size)
                    .totalElements(cachedData.size())
                    .totalPages((cachedData.size() + size - 1) / size);
        }

        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "submissionStatus", "studentName", "totalScore"), sortBy, sortDir);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> {
                    log.error("Challenge not found for challengeId: {}", challengeId);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);

        String role = jwtUtil.extractRoleFromCurrentRequest();
        if (!role.equals("TEACHER") && !role.equals("TEACHING_ASSISTANT")) {
            log.error("Unauthorized access to submissions for challengeId: {} by user with role: {}", challengeId, role);
            throw new ApiException("Unauthorized: Only teachers or teaching assistants can view submissions", HttpStatus.FORBIDDEN.value());
        }

        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<SubmissionDailyChallenge> submissionPage = submissionDailyChallengeRepository
                .findByChallengeIdAndDeletedAtIsNull(challengeId, text == null ? "" : text, pageable);

        List<Long> submissionIds = submissionPage.getContent().stream()
                .map(SubmissionDailyChallenge::getId)
                .toList();

        Map<Long, Double> gradingScoreMap = submissionIds.isEmpty() ? Map.of() :
                gradingDailyChallengeRepository.findBySubmissionDailyIdInAndIsFinalizedTrueAndDeletedAtIsNull(submissionIds)
                        .stream()
                        .collect(Collectors.toMap(
                                g -> g.getSubmissionDaily().getId(),
                                GradingDailyChallenge::getTotalScore,
                                (existing, replacement) -> existing
                        ));

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
                    dto.setTotalScore(gradingScoreMap.get(submission.getId()));
                    return dto;
                })
                .toList();

        cacheService.cacheObject(cacheKey, data, 5); // TTL 5 phút
        log.debug("Cache stored for submissions: {}", cacheKey);

        log.info("Retrieved {} submissions ({} total) for challengeId: {}", data.size(), submissionPage.getTotalElements(), challengeId);

        return DataResponse.success(data, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .page(page)
                .size(size)
                .totalElements(submissionPage.getTotalElements())
                .totalPages(submissionPage.getTotalPages());
    }

    @Override
    @Transactional
    public void autoSubmitExpiredSubmissions() {
        log.info("Processing auto-submit for expired submissions");

        int pageSize = 100;
        Pageable pageable = PageRequest.of(0, pageSize);
        Page<SubmissionDailyChallenge> submissionPage;

        do {
            submissionPage = submissionDailyChallengeRepository.findBySubmissionStatusAndAutoSubmittedFalseAndExpiredAtBefore(
                    SubmissionStatus.PENDING, OffsetDateTime.now(), pageable);

            List<SubmissionDailyChallenge> toUpdate = submissionPage.getContent();
            if (!toUpdate.isEmpty()) {
                toUpdate.forEach(submission -> {
                    submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
                    submission.setAutoSubmitted(true);
                    submission.setSubmittedAt(OffsetDateTime.now());
                });
                submissionDailyChallengeRepository.saveAll(toUpdate);
                log.debug("Auto-submitted {} submissions", toUpdate.size());
            }

            pageable = pageable.next();
        } while (submissionPage.hasNext());

        log.info("Auto-submit task completed");
    }
}