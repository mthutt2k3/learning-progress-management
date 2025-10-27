package com.learning.progress.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learning.progress.common.Const;
import com.learning.progress.common.QuestionType;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.Question;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.QuestionMapper;
import com.learning.progress.repository.ChallengeSectionRepository;
import com.learning.progress.repository.QuestionRepository;
import com.learning.progress.service.CacheService;
import com.learning.progress.service.QuestionService;
import com.learning.progress.service.validator.QuestionValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class QuestionServiceImpl implements QuestionService {

    @Autowired
    private QuestionValidator questionValidator;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private ChallengeSectionRepository sectionRepository;

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private Validator validator;

    @Autowired
    private CacheService cacheService;

    @Override
    @Transactional
    public List<QuestionDto> bulkQuestion(List<QuestionDto> dtos, Long sectionId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Processing bulk question for sectionId: {}", traceId, sectionId);

        ChallengeSection section = sectionRepository.findByIdAndDeletedAtIsNull(sectionId)
                .orElseThrow(() -> new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        List<Question> existingActiveQuestions = questionRepository
                .findBySectionIdAndDeletedAtIsNullOrderByOrderNumberAsc(sectionId);
        Set<Long> existingActiveIds = existingActiveQuestions.stream()
                .map(Question::getId)
                .collect(Collectors.toSet());

        List<QuestionDto> deleteRequests = dtos.stream()
                .filter(QuestionDto::isToBeDeleted)
                .collect(Collectors.toList());
        List<QuestionDto> nonDeletedRequests = dtos.stream()
                .filter(dto -> !dto.isToBeDeleted())
                .collect(Collectors.toList());

        validateBulkRequests(deleteRequests, nonDeletedRequests, existingActiveIds, section, traceId);

        List<QuestionDto> result = processQuestions(deleteRequests, nonDeletedRequests, section, traceId);

        cacheService.clearCacheForSection(sectionId, section.getChallenge().getId(), traceId);
        deleteRequests.forEach(dto -> cacheService.clearCacheForQuestion(dto.getId(), sectionId,
                section.getChallenge().getId(), traceId));
        nonDeletedRequests.forEach(dto -> {
            if (dto.getId() != null) {
                cacheService.clearCacheForQuestion(dto.getId(), sectionId, section.getChallenge().getId(), traceId);
            }
        });

        return result;
    }

    @Override
    public QuestionDto getQuestion(Long id) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Getting question with ID: {}", traceId, id);

        String cacheKey = cacheService.buildQuestionCacheKey(id);
        QuestionDto cachedResult = cacheService.getCachedObject(
                cacheKey, new TypeReference<QuestionDto>() {}, traceId);
        if (cachedResult != null) {
            return cachedResult;
        }

        Question question = questionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.QUESTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        QuestionDto result = questionMapper.toQuestionDto(question);

        cacheService.cacheObject(cacheKey, result, CacheService.QUESTION_TTL_MINUTES, traceId);

        return result;
    }

    @Override
    @Transactional
    public void deleteQuestions(List<Long> ids) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Deleting questions with IDs: {}", traceId, ids);

        if (ids == null || ids.isEmpty()) {
            log.error("[{}] Question IDs list is empty", traceId);
            throw new ApiException(Const.QUESTION.IDS_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        List<Question> questions = questionRepository.findAllByIdInAndDeletedAtIsNull(ids);
        if (questions.isEmpty()) {
            log.error("[{}] No active questions found for IDs: {}", traceId, ids);
            throw new ApiException(Const.QUESTION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
        }

        String deletedBy = jwtUtil.extractEmailPrefixFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();

        questions.forEach(q -> {
            q.setDeletedBy(deletedBy);
            q.setDeletedAt(now);
            cacheService.clearCacheForQuestion(q.getId(), q.getSection().getId(),
                    q.getSection().getChallenge().getId(), traceId);
        });

        questionRepository.saveAll(questions);
        log.info("[{}] Successfully soft deleted {} questions", traceId, questions.size());
    }

    @Override
    public List<QuestionDto> getQuestionsBySection(Long sectionId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Getting questions for sectionId: {}", traceId, sectionId);

        String cacheKey = cacheService.buildQuestionsBySectionCacheKey(sectionId);
        List<QuestionDto> cachedResult = cacheService.getCachedObject(
                cacheKey, new TypeReference<List<QuestionDto>>() {}, traceId);
        if (cachedResult != null) {
            return cachedResult;
        }

        sectionRepository.findByIdAndDeletedAtIsNull(sectionId)
                .orElseThrow(() -> new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        List<Question> questions = questionRepository.findBySectionIdAndDeletedAtIsNull(sectionId);
        List<QuestionDto> result = questionMapper.toQuestionDtos(questions);

        cacheService.cacheObject(cacheKey, result, CacheService.QUESTION_TTL_MINUTES, traceId);

        return result;
    }

    @Override
    @Transactional
    public void updateScoreQuestion(Long questionId, double score) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Updating score for question ID: {}, new score: {}", traceId, questionId, score);

        Question question = questionRepository.findByIdAndDeletedAtIsNull(questionId)
                .orElseThrow(() -> new ApiException(Const.QUESTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        question.setScore(BigDecimal.valueOf(score));
        questionRepository.save(question);

        cacheService.clearCacheForQuestion(questionId, question.getSection().getId(),
                question.getSection().getChallenge().getId(), traceId);

        log.info("[{}] Updated score for question ID {} to {}", traceId, questionId, score);
    }

    private void validateBulkRequests(List<QuestionDto> deleteRequests, List<QuestionDto> nonDeletedRequests,
                                      Set<Long> existingActiveIds, ChallengeSection section, String traceId) {
        for (QuestionDto deleteDto : deleteRequests) {
            Set<ConstraintViolation<QuestionDto>> violations = validator.validate(deleteDto, QuestionDto.Deleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                log.error("[{}] Validation error for delete request: {}", traceId, errorMsg);
                throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
            }

            Long deleteId = deleteDto.getId();
            if (deleteId == null || !existingActiveIds.contains(deleteId)) {
                log.error("[{}] Invalid question ID for deletion: {}", traceId, deleteId);
                throw new ApiException("Question ID to delete does not exist: " + deleteId, HttpStatus.BAD_REQUEST.value());
            }
        }

        Set<Long> requestExistingIds = nonDeletedRequests.stream()
                .filter(dto -> dto.getId() != null)
                .map(QuestionDto::getId)
                .collect(Collectors.toSet());

        Set<Long> requestDeleteIds = deleteRequests.stream()
                .map(QuestionDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> invalidRequestIds = new HashSet<>();
        invalidRequestIds.addAll(requestExistingIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet()));
        invalidRequestIds.addAll(requestDeleteIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet()));

        if (!invalidRequestIds.isEmpty()) {
            log.error("[{}] Invalid question IDs: {}", traceId, invalidRequestIds);
            throw new ApiException("Invalid question IDs: " + invalidRequestIds, HttpStatus.BAD_REQUEST.value());
        }

        Set<Long> handledIds = new HashSet<>();
        handledIds.addAll(requestExistingIds);
        handledIds.addAll(requestDeleteIds);

        Set<Long> unhandledDbIds = existingActiveIds.stream()
                .filter(id -> !handledIds.contains(id))
                .collect(Collectors.toSet());

        if (!unhandledDbIds.isEmpty()) {
            log.error("[{}] Unhandled questions: {}", traceId, unhandledDbIds);
            throw new ApiException(
                    String.format("Questions not handled: %s. FE must include ALL active questions!", unhandledDbIds),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        int expectedNonDeletedCount = existingActiveIds.size() - requestDeleteIds.size();
        int actualNonDeletedCount = nonDeletedRequests.stream()
                .filter(dto -> dto.getId() != null)
                .map(QuestionDto::getId)
                .collect(Collectors.toSet())
                .size();

        if (actualNonDeletedCount != expectedNonDeletedCount) {
            log.error("[{}] Non-deleted questions count mismatch! Expected: {}, Actual: {}", traceId,
                    expectedNonDeletedCount, actualNonDeletedCount);
            throw new ApiException(
                    String.format("Non-deleted questions count mismatch! Expected: %d, Actual: %d",
                            expectedNonDeletedCount, actualNonDeletedCount),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        for (QuestionDto dto : nonDeletedRequests) {
            Set<ConstraintViolation<QuestionDto>> violations = validator.validate(dto, QuestionDto.NotDeleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                log.error("[{}] Validation error for non-deleted request: {}", traceId, errorMsg);
                throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
            }
            questionValidator.validateQuestionDto(dto);
        }

        Set<Integer> orderNumbers = nonDeletedRequests.stream()
                .map(QuestionDto::getOrderNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int nonDeletedSize = nonDeletedRequests.size();
        Set<Integer> expectedOrders = IntStream.rangeClosed(1, nonDeletedSize).boxed().collect(Collectors.toSet());

        if (orderNumbers.size() != nonDeletedSize || !orderNumbers.equals(expectedOrders)) {
            log.error("[{}] Order numbers must be sequential from 1 to {}. Found: {}", traceId, nonDeletedSize, orderNumbers);
            throw new ApiException(
                    String.format("Order numbers must be sequential from 1 to %d. Found: %s", nonDeletedSize, orderNumbers),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        Set<String> questionTextsLower = new HashSet<>();
        for (QuestionDto dto : nonDeletedRequests) {
            String text = dto.getQuestionText().trim().toLowerCase();
            if (!questionTextsLower.add(text)) {
                log.error("[{}] Duplicate question text: {}", traceId, dto.getQuestionText());
                throw new ApiException(
                        String.format("Duplicate question text: %s", dto.getQuestionText()),
                        HttpStatus.BAD_REQUEST.value()
                );
            }
        }

        for (QuestionDto dto : nonDeletedRequests) {
            if (dto.getId() == null) {
                String trimmedText = dto.getQuestionText().trim();
                boolean exists = questionRepository.existsBySectionAndQuestionTextIgnoreCaseAndDeletedAtIsNull(section, trimmedText);
                if (exists) {
                    log.error("[{}] Question text already exists: {}", traceId, trimmedText);
                    throw new ApiException(
                            String.format("Question text '%s' already exists in this section", trimmedText),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }
            }
        }
    }

    private List<QuestionDto> processQuestions(List<QuestionDto> deleteRequests, List<QuestionDto> nonDeletedRequests,
                                               ChallengeSection section, String traceId) {
        List<QuestionDto> result = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (Long deleteId : deleteRequests.stream().map(QuestionDto::getId).filter(Objects::nonNull).collect(Collectors.toList())) {
            Question question = questionRepository.findById(deleteId)
                    .filter(q -> q.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Question not found: " + deleteId, HttpStatus.NOT_FOUND.value()));
            question.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
            question.setDeletedAt(now);
            questionRepository.save(question);
        }

        List<QuestionDto> updateRequests = nonDeletedRequests.stream()
                .filter(dto -> dto.getId() != null)
                .collect(Collectors.toList());

        for (QuestionDto dto : updateRequests) {
            Question question = questionRepository.findById(dto.getId())
                    .filter(q -> q.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Question not found: " + dto.getId(), HttpStatus.NOT_FOUND.value()));
            question.setQuestionText(dto.getQuestionText());
            question.setScore(BigDecimal.valueOf(dto.getScore()));
            question.setQuestionType(QuestionType.valueOf(dto.getQuestionType()));
            question.setOrderNumber(dto.getOrderNumber());
            question.setQuestionContentJson(JsonUtil.objectToMap(dto.getContent()));
            result.add(questionMapper.toQuestionDto(questionRepository.save(question)));
        }

        List<QuestionDto> newRequests = nonDeletedRequests.stream()
                .filter(dto -> dto.getId() == null)
                .collect(Collectors.toList());

        for (QuestionDto dto : newRequests) {
            Question newQuestion = new Question();
            newQuestion.setSection(section);
            newQuestion.setQuestionText(dto.getQuestionText());
            newQuestion.setScore(BigDecimal.valueOf(dto.getScore()));
            newQuestion.setQuestionType(QuestionType.valueOf(dto.getQuestionType()));
            newQuestion.setOrderNumber(dto.getOrderNumber());
            newQuestion.setQuestionContentJson(JsonUtil.objectToMap(dto.getContent()));
            result.add(questionMapper.toQuestionDto(questionRepository.save(newQuestion)));
        }

        return result;
    }
}