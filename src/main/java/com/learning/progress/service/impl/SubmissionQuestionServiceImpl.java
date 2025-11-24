package com.learning.progress.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learning.progress.common.*;
import com.learning.progress.dto.challenge.section.DataContent;
import com.learning.progress.dto.challenge.section.SectionDto;
import com.learning.progress.dto.challenge.section.StudentDataContent;
import com.learning.progress.dto.submission.AnswerContent;
import com.learning.progress.dto.submission.DraftSubmissionResponse;
import com.learning.progress.dto.submission.SaveSubmissionRequest;
import com.learning.progress.dto.submission.SubmissionResultResponse;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ChallengeSectionMapper;
import com.learning.progress.repository.*;
import com.learning.progress.cache.CacheService;
import com.learning.progress.service.NotificationService;
import com.learning.progress.service.SubmissionQuestionService;
import com.learning.progress.service.validator.SubmissionQuestionValidator;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SubmissionQuestionServiceImpl implements SubmissionQuestionService {

    @Autowired private SubmissionDailyChallengeRepository submissionDailyChallengeRepository;
    @Autowired private SubmissionQuestionRepository submissionQuestionRepository;
    @Autowired private AppValidator appValidator;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private SubmissionQuestionValidator submissionQuestionValidator;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private CacheService cacheService;
    @Autowired
    private ChallengeSectionRepository challengeSectionRepository;
    @Autowired
    private GradingQuestionRepository gradingQuestionRepository;
    @Autowired
    private ChallengeSectionMapper challengeSectionMapper;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private ClassStudentRepository classStudentRepository;
    @Autowired
    private ClassTeacherRepository classTeacherRepository;

    @Override
    @Transactional(readOnly = true)
    public SubmissionResultResponse getSubmissionResult(Long submissionChallengeId) {
        final String action = "getSubmissionResult";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter {} submissionChallengeId={}", traceId, action, submissionChallengeId);

        Long userId = jwtUtil.extractUserIdFromCurrentRequest();
        String cacheKey = cacheService.buildSubmissionResultCacheKey(userId, submissionChallengeId);

        SubmissionResultResponse cached = cacheService.getCachedObject(cacheKey, new TypeReference<>() {});
        if (cached != null) {
            log.debug("[{}] {} cache HIT key={}", traceId, action, cacheKey);
            log.info("[{}] exit {} (cache) submissionChallengeId={}", traceId, action, submissionChallengeId);
            return cached;
        }
        log.debug("[{}] {} cache MISS key={}", traceId, action, cacheKey);

        // 1. Lấy submission + validate access via centralized helper
        SubmissionDailyChallenge submission = appValidator.validateUserAccessToSubmissionResult(submissionChallengeId);
        log.debug("[{}] {} loaded submission id={} status={}", traceId, action, submission.getId(), submission.getSubmissionStatus());

        DailyChallenge challenge = submission.getChallenge();
        Long challengeId = challenge.getId();

        // 2. LẤY TẤT CẢ SECTIONS + QUESTIONS (chỉ 1 query nhờ JOIN FETCH)
        List<ChallengeSection> sections = challengeSectionRepository
                .findByChallengeIdAndDeletedAtIsNullOrderByOrderNumberAsc(challengeId);

        if (sections.isEmpty()) {
            log.error("[{}] {} {}", traceId, action, Const.SUBMISSION.NO_SECTIONS_FOR_CHALLENGE);
            throw new ApiException(Const.SUBMISSION.NO_SECTIONS_FOR_CHALLENGE, HttpStatus.NOT_FOUND.value());
        }
        log.debug("[{}] {} loaded {} sections for challengeId={}", traceId, action, sections.size(), challengeId);

        // 3. Lấy submission questions
        List<SubmissionQuestion> submissionQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionChallengeId);

        Map<Long, SubmissionQuestion> submissionQuestionMap = submissionQuestions.stream()
                .collect(Collectors.toMap(sq -> sq.getQuestion().getId(), sq -> sq, (e1, e2) -> e1));
        Map<Long, BigDecimal> receivedScoreMap = gradingQuestionRepository
                .findBySubmissionQuestion_SubmissionDaily_IdAndDeletedAtIsNull(submissionChallengeId)
                .stream()
                .collect(Collectors.toMap(
                        gq -> gq.getSubmissionQuestion().getQuestion().getId(),
                        gq -> BigDecimal.valueOf(gq.getReceivedWeight() == null ? 0.0 : gq.getReceivedWeight()),
                        (v1, v2) -> v1
                ));
        log.debug("[{}] {} loaded {} submissionQuestions and {} grading entries", traceId, action, submissionQuestions.size(), receivedScoreMap.size());

        // 4. Xây dựng response
        List<SubmissionResultResponse.SectionDetailDTO> sectionDetails = sections.stream()
                .map(section -> {
                    SubmissionResultResponse.SectionDetailDTO dto = new SubmissionResultResponse.SectionDetailDTO();

                    // Map Section
                    SectionDto sectionInfo = new SectionDto();
                    sectionInfo.setId(section.getId());
                    sectionInfo.setSectionTitle(section.getSectionTitle());
                    sectionInfo.setSectionsUrl(section.getSectionsUrl());
                    sectionInfo.setSectionsContent(section.getSectionsContent());
                    sectionInfo.setOrderNumber(section.getOrderNumber());
                    sectionInfo.setResourceType(section.getResourceType().name());
                    dto.setSection(sectionInfo);

                    // Map Questions (đã được load sẵn + sort + filter deleted)
                    List<SubmissionResultResponse.QuestionResult> questionResults = section.getQuestions().stream()
                            .map(question -> {
                                SubmissionResultResponse.QuestionResult qr = new SubmissionResultResponse.QuestionResult();
                                qr.setQuestionId(question.getId());
                                qr.setQuestionText(question.getQuestionText());
                                qr.setQuestionType(question.getQuestionType());
                                qr.setOrderNumber(question.getOrderNumber());
                                qr.setScore(question.getWeight());
                                qr.setReceivedScore(receivedScoreMap.get(question.getId()));

                                // Parse question content
                                DataContent questionContent = JsonUtil.responseToObject(
                                        question.getQuestionContentJson(), DataContent.class);
                                qr.setQuestionContent(questionContent);

                                // Parse submitted answer
                                SubmissionQuestion sq = submissionQuestionMap.get(question.getId());
                                if (sq != null && sq.getSubmissionContentJson() != null) {
                                    qr.setSubmissionQuestionId(sq.getId());
                                    AnswerContent submittedContent = JsonUtil.responseToObject(
                                            sq.getSubmissionContentJson(), AnswerContent.class);
                                    qr.setSubmittedContent(submittedContent);
                                } else {
                                    qr.setSubmittedContent(null);
                                }

                                return qr;
                            })
                            .toList();

                    dto.setQuestionResults(questionResults);
                    return dto;
                })
                .toList();

        // 5. Build response
        SubmissionResultResponse response = new SubmissionResultResponse();
        response.setChallengeId(challengeId);
        response.setSubmissionChallengeId(submissionChallengeId);
        response.setSectionDetails(sectionDetails);

        log.info("[{}] {} Retrieved submission result for challengeId={} submissionId={}", traceId, action, challengeId, submissionChallengeId);

        // 6. Cache
        if (submission.getSubmissionStatus() == SubmissionStatus.SUBMITTED ||
                submission.getSubmissionStatus() == SubmissionStatus.GRADED) {
            cacheService.cacheObject(cacheKey, response, 10);
            log.debug("[{}] {} cached response key={} ttl={}min", traceId, action, cacheKey, 10);
        }

        log.info("[{}] exit {} submissionChallengeId={}", traceId, action, submissionChallengeId);
        return response;
    }

    // SubmissionQuestionServiceImpl.java
    @Override
    @Transactional(readOnly = true)
    public DraftSubmissionResponse getDraftSubmission(Long submissionChallengeId) {
        final String action = "getDraftSubmission";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter {} submissionChallengeId={}", traceId, action, submissionChallengeId);

        // 1. Lấy submission + validate access
        SubmissionDailyChallenge submission = appValidator.validateUserAccessToSubmission(submissionChallengeId);
        log.debug("[{}] {} loaded submission id={} status={}", traceId, action, submission.getId(), submission.getSubmissionStatus());

        SubmissionStatus status = submission.getSubmissionStatus();

        if (status == SubmissionStatus.PENDING) {
            submission.setSubmissionStatus(SubmissionStatus.DRAFT);
            submissionDailyChallengeRepository.save(submission);
            log.debug("[{}] {} changed status PENDING->DRAFT for submissionId={}", traceId, action, submission.getId());
        }

        DailyChallenge challenge = submission.getChallenge();
        Long challengeId = challenge.getId();

        // 2. Load sections + questions
        List<ChallengeSection> sections = challengeSectionRepository
                .findByChallengeIdAndDeletedAtIsNullOrderByOrderNumberAsc(challengeId);
        log.debug("[{}] {} loaded {} sections", traceId, action, sections.size());

        // 3. Load submitted answers
        List<SubmissionQuestion> submissionQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionChallengeId);
        log.debug("[{}] {} loaded {} submissionQuestions", traceId, action, submissionQuestions.size());

        Map<Long, SubmissionQuestion> submittedMap = submissionQuestions.stream()
                .collect(Collectors.toMap(sq -> sq.getQuestion().getId(), sq -> sq));

        // 4. Build response
        List<DraftSubmissionResponse.SectionDraftDTO> sectionDtos = sections.stream()
                .map(section -> {
                    DraftSubmissionResponse.SectionDraftDTO secDto = new DraftSubmissionResponse.SectionDraftDTO();

                    // Section info
                    SectionDto sectionInfo = new SectionDto();
                    sectionInfo.setId(section.getId());
                    sectionInfo.setSectionsUrl(section.getSectionsUrl());
                    sectionInfo.setSectionsContent(section.getSectionsContent());
                    sectionInfo.setResourceType(section.getResourceType().name());
                    sectionInfo.setSectionTitle(section.getSectionTitle());
                    sectionInfo.setOrderNumber(section.getOrderNumber());
                    secDto.setSection(sectionInfo);

                    // Questions (student view + submitted)
                    List<DraftSubmissionResponse.QuestionDraftDTO> qDtos = section.getQuestions().stream()
                            .map(q -> {
                                DraftSubmissionResponse.QuestionDraftDTO qDto = new DraftSubmissionResponse.QuestionDraftDTO();
                                qDto.setQuestionId(q.getId());
                                qDto.setQuestionText(q.getQuestionText());
                                qDto.setOrderNumber(q.getOrderNumber());
                                qDto.setScore(q.getWeight());
                                qDto.setQuestionType(q.getQuestionType());

                                // Nội dung câu hỏi: không có đáp án đúng
                                DataContent fullContent = JsonUtil.responseToObject(q.getQuestionContentJson(), DataContent.class);
                                StudentDataContent studentContent = challengeSectionMapper.toStudentDataContent(fullContent, q.getQuestionType());
                                qDto.setContent(studentContent);

                                // Câu trả lời đã chọn
                                SubmissionQuestion sq = submittedMap.get(q.getId());
                                if (sq != null && sq.getSubmissionContentJson() != null) {
                                    qDto.setSubmissionQuestionId(sq.getId());
                                    AnswerContent answer = JsonUtil.responseToObject(sq.getSubmissionContentJson(), AnswerContent.class);
                                    qDto.setSubmittedContent(answer);
                                }

                                return qDto;
                            })
                            .toList();

                    secDto.setQuestions(qDtos);
                    return secDto;
                })
                .toList();

        DraftSubmissionResponse response = new DraftSubmissionResponse();
        response.setChallengeId(challengeId);
        response.setSubmissionChallengeId(submissionChallengeId);
        response.setStatus(submission.getSubmissionStatus());
        response.setSectionDetails(sectionDtos);

        log.info("[{}] exit {} submissionChallengeId={} sectionsReturned={}", traceId, action, submissionChallengeId, sectionDtos.size());
        return response;
    }

    @Override
    @Transactional
    public void saveSubmission(Long submissionChallengeId, SaveSubmissionRequest request) {
        final String action = "saveSubmission";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter {} submissionChallengeId={} saveAsDraft={}", traceId, action, submissionChallengeId, request.getSaveAsDraft());

        SubmissionDailyChallenge submission = appValidator.validateUserAccessToSubmission(submissionChallengeId);
        log.debug("[{}] {} validated access for submissionId={} userId={}", traceId, action, submission.getId(), submission.getUser().getId());

        DailyChallenge dailyChallenge = submission.getChallenge();
        Long userId = jwtUtil.extractUserIdFromCurrentRequest();

        appValidator.validateClassIsActive(dailyChallenge.getClassLesson().getClassChapter().getClazz().getId());
        appValidator.validateUserAccessToClass(dailyChallenge.getClassLesson().getClassChapter().getClazz().getId());

        SubmissionStatus status = submission.getSubmissionStatus();
        if (status == SubmissionStatus.SUBMITTED || status == SubmissionStatus.GRADED) {
            log.warn("[{}] {} attempt to save completed submissionId={} status={}", traceId, action, submission.getId(), status);
            throw new ApiException(Const.SUBMISSION.ALREADY_COMPLETED, HttpStatus.BAD_REQUEST.value());
        }
        if (status == SubmissionStatus.MISSED) {
            log.warn("[{}] {} attempt to save missed submissionId={}", traceId, action, submission.getId());
            throw new ApiException(Const.SUBMISSION.SUBMISSION_MISSED, HttpStatus.BAD_REQUEST.value());
        }

        submissionQuestionValidator.validateSubmissionQuestions(dailyChallenge.getId(), request);
        log.debug("[{}] {} submission questions validated for challengeId={}", traceId, action, dailyChallenge.getId());

        List<SubmissionQuestion> existingQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submission.getId());
        Map<Long, SubmissionQuestion> existingMap = existingQuestions.stream()
                .collect(Collectors.toMap(sq -> sq.getQuestion().getId(), sq -> sq, (e, r) -> e));
        log.debug("[{}] {} loaded {} existing submission questions", traceId, action, existingQuestions.size());

        List<Long> questionIds = request.getQuestionAnswers().stream()
                .map(SaveSubmissionRequest.QuestionAnswer::getQuestionId)
                .toList();

        // Kiểm tra: các questionId gửi lên phải tồn tại và chưa bị xóa
        List<Question> questions = questionIds.isEmpty() ? List.of() :
                questionRepository.findAllByIdInAndDeletedAtIsNull(questionIds);
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // Kiểm tra: tất cả questionId trong request phải tồn tại
        for (Long qid : questionIds) {
            if (!questionMap.containsKey(qid)) {
                String msg = String.format(Const.QUESTION.QUESTION_NOT_FOUND_WITH_ID, qid);
                log.error("[{}] {} {}", traceId, action, msg);
                throw new ApiException(msg, HttpStatus.NOT_FOUND.value());
            }
        }

        List<SubmissionQuestion> toSave = new ArrayList<>();
        for (SaveSubmissionRequest.QuestionAnswer answer : request.getQuestionAnswers()) {
            Long qid = answer.getQuestionId();
            Question question = questionMap.get(qid);

            SubmissionQuestion sq = existingMap.get(qid);
            if (sq != null) {
                sq.setSubmissionContentJson(JsonUtil.objectToMap(answer.getContent()));
                log.debug("[{}] {} updated SubmissionQuestion id={} for questionId={}", traceId, action, sq.getId(), qid);
            } else {
                sq = new SubmissionQuestion();
                sq.setSubmissionDaily(submission);
                sq.setQuestion(question);
                sq.setSubmissionContentJson(JsonUtil.objectToMap(answer.getContent()));
                log.debug("[{}] {} created new SubmissionQuestion for questionId={}", traceId, action, qid);
            }
            toSave.add(sq);
        }

        if (!toSave.isEmpty()) {
            if (status != SubmissionStatus.DRAFT) {
                submission.setSubmissionStatus(SubmissionStatus.DRAFT);
                submissionDailyChallengeRepository.save(submission);
                log.debug("[{}] {} set submission status to DRAFT for submissionId={}", traceId, action, submission.getId());
            }
            submissionQuestionRepository.saveAll(toSave);
            log.info("[{}] {} saved {} submissionQuestion(s) for submissionId={}", traceId, action, toSave.size(), submission.getId());
        } else {
            log.debug("[{}] {} nothing to save", traceId, action);
        }

        // === CHỈ KHI NỘP CHÍNH THỨC ===
        if (Boolean.FALSE.equals(request.getSaveAsDraft())) {
            submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
            submission.setSubmittedAt(OffsetDateTime.now());
            submissionDailyChallengeRepository.saveAndFlush(submission);
            log.info("[{}] {} submission submitted submissionId={} userId={}", traceId, action, submission.getId(), userId);

            String title = Const.NOTIFICATION.SUBMISSION_TITLE;
            String message = String.format(Const.NOTIFICATION.SUBMISSION_MESSAGE_TEMPLATE, dailyChallenge.getChallengeName());
            notificationService.createNotification(userId, null, title, message, null, null);
            log.debug("[{}] {} notification sent for submissionId={} userId={}", traceId, action, submission.getId(), userId);

            cacheService.clearSubmissionsCacheForChallenge(dailyChallenge.getId());
            log.debug("[{}] {} cleared submissions cache for challengeId={}", traceId, action, dailyChallenge.getId());
        }
        // XÓA CACHE
        cacheService.clearSubmissionCache(userId, submissionChallengeId);
        log.debug("[{}] {} cleared submission cache userId={} submissionId={}", traceId, action, userId, submissionChallengeId);

        // === SAU KHI SAVE SUBMISSION + CLEAR CACHE ===
        Long challengeId = dailyChallenge.getId();
        Long classId = dailyChallenge.getClassLesson().getClassChapter().getClazz().getId();

        // Lấy danh sách giáo viên + trợ giảng
        List<ClassTeacher> teachers = classTeacherRepository
                .findByClazzIdAndStatusIn(classId, List.of(ClassTeacherStatus.ACTIVE));
        log.debug("[{}] {} notifying {} teachers for challengeId={}", traceId, action, teachers.size(), challengeId);

        long submittedCount = submissionDailyChallengeRepository
                .countByChallengeIdAndSubmittedAtIsNotNullAndDeletedAtIsNull(challengeId);
        long totalStudents = classStudentRepository
                .countByClassIdAndStatus(classId, ClassStudentStatus.ACTIVE);

        for (ClassTeacher ct : teachers) {
            String basePath = RoleInClass.TEACHER.equals(ct.getRoleInClass())
                    ? "/teacher/daily-challenges/detail/"
                    : "/teaching-assistant/daily-challenges/detail/";
            String url = basePath + challengeId + "/submissions";

            String title = Const.NOTIFICATION.SUBMISSION_STATUS_UPDATE_TITLE;
            String message = String.format(Const.NOTIFICATION.SUBMISSION_STATUS_UPDATE_MESSAGE_TEMPLATE, submittedCount, totalStudents);

            try {
                notificationService.createNotification(ct.getUser().getId(), challengeId, title, message, url, null);
                log.debug("[{}] {} teacher notified userId={} url={}", traceId, action, ct.getUser().getId(), url);
            } catch (Exception ex) {
                log.warn("[{}] {} failed to notify teacherId={} error={}", traceId, action, ct.getUser().getId(), ex.getMessage());
            }
        }
        log.info("[{}] exit {} submissionChallengeId={}", traceId, action, submissionChallengeId);
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionResultResponse.QuestionResult getQuestionDetail(Long submissionQuestionId) {
        final String action = "getQuestionDetail";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter {} submissionQuestionId={}", traceId, action, submissionQuestionId);

        SubmissionQuestion sq = submissionQuestionRepository.findById(submissionQuestionId)
                .orElseThrow(() -> {
                    log.error("[{}] {} submissionQuestion not found id={}", traceId, action, submissionQuestionId);
                    return new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
        if (sq.getDeletedAt() != null) {
            log.warn("[{}] {} submissionQuestion was deleted id={}", traceId, action, submissionQuestionId);
            throw new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
        }

        SubmissionDailyChallenge submission = sq.getSubmissionDaily();
        // Validate access using centralized helper (will throw if unauthorized)
        appValidator.validateUserAccessToSubmission(submission.getId());
        log.debug("[{}] {} access validated for submissionId={} callerUserId={}", traceId, action, submission.getId(), jwtUtil.extractUserIdFromCurrentRequest());

        Question q = sq.getQuestion();

        SubmissionResultResponse.QuestionResult qr = new SubmissionResultResponse.QuestionResult();
        qr.setSubmissionQuestionId(submissionQuestionId);
        if (q != null) {
            qr.setQuestionId(q.getId());
            qr.setQuestionText(q.getQuestionText());
            qr.setQuestionType(q.getQuestionType());
            qr.setOrderNumber(q.getOrderNumber());
            qr.setScore(q.getWeight());
            // question content
            DataContent questionContent = JsonUtil.responseToObject(q.getQuestionContentJson(), DataContent.class);
            qr.setQuestionContent(questionContent);
        }

        // submitted answer (if any)
        if (sq.getSubmissionContentJson() != null) {
            AnswerContent submitted = JsonUtil.responseToObject(sq.getSubmissionContentJson(), AnswerContent.class);
            qr.setSubmittedContent(submitted);
            log.debug("[{}] {} found submitted content for submissionQuestionId={}", traceId, action, submissionQuestionId);
        } else {
            log.debug("[{}] {} no submitted content for submissionQuestionId={}", traceId, action, submissionQuestionId);
        }

        // received score from grading question (if exists)
        gradingQuestionRepository.findBySubmissionQuestionIdAndDeletedAtIsNull(submissionQuestionId)
                .ifPresent(gq -> {
                    qr.setReceivedScore(BigDecimal.valueOf(gq.getReceivedWeight() == null ? 0.0 : gq.getReceivedWeight()));
                    log.debug("[{}] {} grading found for submissionQuestionId={} receivedWeight={}", traceId, action, submissionQuestionId, gq.getReceivedWeight());
                });

        log.info("[{}] exit {} submissionQuestionId={}", traceId, action, submissionQuestionId);
        return qr;
    }
}
