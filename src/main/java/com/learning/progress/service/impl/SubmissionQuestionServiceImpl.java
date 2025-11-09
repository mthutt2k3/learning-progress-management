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
import com.learning.progress.job.QuartzJobTrigger;
import com.learning.progress.mapper.ChallengeSectionMapper;
import com.learning.progress.repository.*;
import com.learning.progress.cache.CacheService;
import com.learning.progress.service.GradingDailyChallengeService;
import com.learning.progress.service.NotificationService;
import com.learning.progress.service.SubmissionQuestionService;
import com.learning.progress.service.validator.SubmissionQuestionValidator;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    @Autowired private GradingDailyChallengeService gradingDailyChallengeService;
    @Autowired private CacheService cacheService;
    @Autowired
    private QuartzJobTrigger quartzJobTrigger;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ChallengeSectionRepository challengeSectionRepository;
    @Autowired
    private GradingQuestionRepository gradingQuestionRepository;
    @Autowired
    private ChallengeSectionMapper challengeSectionMapper;
    @Autowired
    private NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public SubmissionResultResponse getSubmissionResult(Long submissionChallengeId) {
        Long userId = jwtUtil.extractUserIdFromCurrentRequest();
        String cacheKey = cacheService.buildSubmissionResultCacheKey(userId, submissionChallengeId);

        SubmissionResultResponse cached = cacheService.getCachedObject(cacheKey, new TypeReference<>() {});
        if (cached != null) {
            log.debug("Cache HIT for submission result: {}", cacheKey);
            return cached;
        }

        // 1. Lấy submission + validate access via centralized helper
        SubmissionDailyChallenge submission = appValidator.validateUserAccessToSubmissionResult(submissionChallengeId);

        DailyChallenge challenge = submission.getChallenge();
        Long challengeId = challenge.getId();

        // 2. LẤY TẤT CẢ SECTIONS + QUESTIONS (chỉ 1 query nhờ JOIN FETCH)
        List<ChallengeSection> sections = challengeSectionRepository
                .findByChallengeIdAndDeletedAtIsNullOrderByOrderNumberAsc(challengeId);

        if (sections.isEmpty()) {
            throw new ApiException("No sections found for challenge", HttpStatus.NOT_FOUND.value());
        }

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

        log.info("Retrieved submission result for challengeId: {}, submissionId: {}", challengeId, submissionChallengeId);

        // 6. Cache
        if (submission.getSubmissionStatus() == SubmissionStatus.SUBMITTED ||
                submission.getSubmissionStatus() == SubmissionStatus.GRADED) {
            cacheService.cacheObject(cacheKey, response, 10);
        }

        return response;
    }

    // SubmissionQuestionServiceImpl.java
    @Override
    @Transactional(readOnly = true)
    public DraftSubmissionResponse getDraftSubmission(Long submissionChallengeId) {
        // 1. Lấy submission + validate access
        SubmissionDailyChallenge submission = appValidator.validateUserAccessToSubmission(submissionChallengeId);

        SubmissionStatus status = submission.getSubmissionStatus();

        if (status == SubmissionStatus.PENDING) {
            submission.setSubmissionStatus(SubmissionStatus.DRAFT);
            submissionDailyChallengeRepository.save(submission);
        } else if (status != SubmissionStatus.DRAFT) {
            throw new ApiException("Submission is not in draft mode", HttpStatus.BAD_REQUEST.value());
        }

        DailyChallenge challenge = submission.getChallenge();
        Long challengeId = challenge.getId();

        // 2. Load sections + questions
        List<ChallengeSection> sections = challengeSectionRepository
                .findByChallengeIdAndDeletedAtIsNullOrderByOrderNumberAsc(challengeId);

        // 3. Load submitted answers
        List<SubmissionQuestion> submissionQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionChallengeId);

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

        return response;
    }

    @Override
    @Transactional
    public void saveSubmission(Long submissionChallengeId, SaveSubmissionRequest request) {
        SubmissionDailyChallenge submission = appValidator.validateUserAccessToSubmission(submissionChallengeId);

        DailyChallenge dailyChallenge = submission.getChallenge();
        Long userId = jwtUtil.extractUserIdFromCurrentRequest();

        SubmissionStatus status = submission.getSubmissionStatus();
        if (status == SubmissionStatus.SUBMITTED || status == SubmissionStatus.GRADED) {
            throw new ApiException("Submission already completed", HttpStatus.BAD_REQUEST.value());
        }
         submissionQuestionValidator.validateSubmissionQuestions(dailyChallenge.getId(), request);

        List<SubmissionQuestion> existingQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submission.getId());
        Map<Long, SubmissionQuestion> existingMap = existingQuestions.stream()
                .collect(Collectors.toMap(sq -> sq.getQuestion().getId(), sq -> sq, (e, r) -> e));

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
                throw new ApiException("Question not found or deleted: " + qid, HttpStatus.NOT_FOUND.value());
            }
        }

        List<SubmissionQuestion> toSave = new ArrayList<>();
        for (SaveSubmissionRequest.QuestionAnswer answer : request.getQuestionAnswers()) {
            Long qid = answer.getQuestionId();
            Question question = questionMap.get(qid);

            SubmissionQuestion sq = existingMap.get(qid);
            if (sq != null) {
                sq.setSubmissionContentJson(JsonUtil.objectToMap(answer.getContent()));
            } else {
                sq = new SubmissionQuestion();
                sq.setSubmissionDaily(submission);
                sq.setQuestion(question);
                sq.setSubmissionContentJson(JsonUtil.objectToMap(answer.getContent()));
            }
            toSave.add(sq);
        }

        if (!toSave.isEmpty()) {
            if (status != SubmissionStatus.DRAFT) {
                submission.setSubmissionStatus(SubmissionStatus.DRAFT);
                submissionDailyChallengeRepository.save(submission);
            }
            submissionQuestionRepository.saveAll(toSave);
        }

        // === CHỈ KHI NỘP CHÍNH THỨC ===
        if (!request.getSaveAsDraft()) {
            submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
            submission.setSubmittedAt(OffsetDateTime.now());
            submissionDailyChallengeRepository.saveAndFlush(submission);

            String title = Const.NOTIFICATION.SUBMISSION_TITLE;
            String message = String.format(Const.NOTIFICATION.SUBMISSION_MESSAGE_TEMPLATE, dailyChallenge.getChallengeName());
            notificationService.createNotification(userId, null, title, message, null, null);

            cacheService.clearSubmissionsCacheForChallenge(dailyChallenge.getId());
        }
        // XÓA CACHE
        cacheService.clearSubmissionCache(userId, submissionChallengeId);
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionResultResponse.QuestionResult getQuestionDetail(Long submissionQuestionId) {
        SubmissionQuestion sq = submissionQuestionRepository.findById(submissionQuestionId)
                .orElseThrow(() -> new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        if (sq.getDeletedAt() != null) {
            throw new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
        }

        SubmissionDailyChallenge submission = sq.getSubmissionDaily();

        // Validate access using centralized helper (will throw if unauthorized)
        appValidator.validateUserAccessToSubmission(submission.getId());

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
        }

        // received score from grading question (if exists)
        gradingQuestionRepository.findBySubmissionQuestionIdAndDeletedAtIsNull(submissionQuestionId)
                .ifPresent(gq -> qr.setReceivedScore(BigDecimal.valueOf(gq.getReceivedWeight() == null ? 0.0 : gq.getReceivedWeight())));

        return qr;
    }
}
