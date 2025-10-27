package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.QuestionType;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.Question;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.QuestionMapper;
import com.learning.progress.repository.ChallengeSectionRepository;
import com.learning.progress.repository.QuestionRepository;
import com.learning.progress.service.QuestionService;
import com.learning.progress.service.validator.QuestionValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Override
    @Transactional
    public List<QuestionDto> bulkQuestion(List<QuestionDto> dtos, Long sectionId) {
        String traceId = TraceUtil.getTraceId();

        // Bước 1: Validate Section
        ChallengeSection section = sectionRepository.findByIdAndDeletedAtIsNull(sectionId)
                .orElseThrow(() -> {
                    log.error("[{}] Section not found: {}", traceId, sectionId);
                    return new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Bước 2: Load Existing Active Questions
        List<Question> existingActiveQuestions = questionRepository
                .findBySectionIdAndDeletedAtIsNullOrderByOrderNumberAsc(sectionId);
        Set<Long> existingActiveIds = existingActiveQuestions.stream()
                .map(Question::getId)
                .collect(Collectors.toSet());

        // Bước 3: Phân loại Requests
        List<QuestionDto> deleteRequests = dtos.stream()
                .filter(QuestionDto::isToBeDeleted)
                .collect(Collectors.toList());
        List<QuestionDto> nonDeletedRequests = dtos.stream()
                .filter(dto -> !dto.isToBeDeleted())
                .collect(Collectors.toList());

        // Bước 4: Validate DELETE requests
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

        // Bước 5: Validate EXISTING IDs - Strict Matching
        Set<Long> requestExistingIds = nonDeletedRequests.stream()
                .filter(dto -> dto.getId() != null)
                .map(QuestionDto::getId)
                .collect(Collectors.toSet());

        Set<Long> requestDeleteIds = deleteRequests.stream()
                .map(QuestionDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Check 1: All request IDs must exist
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

        // Check 2: All DB questions must be handled
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

        // Check 3: Count consistency
        int expectedNonDeletedCount = existingActiveQuestions.size() - requestDeleteIds.size();
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

        // Bước 6: Bean Validation Non-Deleted
        for (QuestionDto dto : nonDeletedRequests) {
            Set<ConstraintViolation<QuestionDto>> violations = validator.validate(dto, QuestionDto.NotDeleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                log.error("[{}] Validation error for non-deleted request: {}", traceId, errorMsg);
                throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
            }
            questionValidator.validateQuestionDto(dto);
        }

        // Bước 7: Validate Order Numbers (sequential from 1)
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

        // Bước 8: Process
        OffsetDateTime now = OffsetDateTime.now();
        List<QuestionDto> result = new ArrayList<>();

        // Process DELETE
        for (Long deleteId : requestDeleteIds) {
            Question question = questionRepository.findById(deleteId)
                    .filter(q -> q.getDeletedAt() == null)
                    .orElseThrow(() -> {
                        log.error("[{}] Question not found for deletion: {}", traceId, deleteId);
                        return new ApiException("Question not found: " + deleteId, HttpStatus.NOT_FOUND.value());
                    });
            question.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
            question.setDeletedAt(now);
            questionRepository.save(question);
        }

        // Process UPDATE existing
        List<QuestionDto> updateRequests = nonDeletedRequests.stream()
                .filter(dto -> dto.getId() != null)
                .collect(Collectors.toList());

        for (QuestionDto dto : updateRequests) {
            Question question = questionRepository.findById(dto.getId())
                    .filter(q -> q.getDeletedAt() == null)
                    .orElseThrow(() -> {
                        log.error("[{}] Question not found: {}", traceId, dto.getId());
                        return new ApiException("Question not found: " + dto.getId(), HttpStatus.NOT_FOUND.value());
                    });

            question.setQuestionText(dto.getQuestionText());
            question.setScore(BigDecimal.valueOf(dto.getScore()));
            question.setQuestionType(QuestionType.valueOf(dto.getQuestionType()));
            question.setOrderNumber(dto.getOrderNumber());
            question.setQuestionContentJson(JsonUtil.objectToMap(dto.getContent()));
            result.add(questionMapper.toQuestionDto(questionRepository.save(question)));
        }

        // Process CREATE new
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

    @Override
    public QuestionDto getQuestion(Long id) {
        Question question = questionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.QUESTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        return questionMapper.toQuestionDto(question);
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

        // Lấy danh sách các câu hỏi hợp lệ (chưa bị xóa)
        List<Question> questions = questionRepository.findAllByIdInAndDeletedAtIsNull(ids);

        if (questions.isEmpty()) {
            log.error("[{}] No active questions found for IDs: {}", traceId, ids);
            throw new ApiException(Const.QUESTION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
        }

        // Cập nhật thông tin xóa mềm
        String deletedBy = jwtUtil.extractEmailPrefixFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();

        questions.forEach(q -> {
            q.setDeletedBy(deletedBy);
            q.setDeletedAt(now);
        });

        questionRepository.saveAll(questions);

        log.info("[{}] Successfully soft deleted {} questions", traceId, questions.size());
    }


    @Override
    public List<QuestionDto> getQuestionsBySection(Long sectionId) {
        // Kiểm tra sự tồn tại của section
        sectionRepository.findByIdAndDeletedAtIsNull(sectionId)
                .orElseThrow(() -> new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Lấy danh sách câu hỏi theo sectionId
        List<Question> questions = questionRepository.findBySectionIdAndDeletedAtIsNull(sectionId);

        // Ánh xạ sang QuestionDto
        return questionMapper.toQuestionDtos(questions);
    }

    @Override
    public void updateScoreQuestion(Long questionId, double score) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Updating score for question ID: {}, new score: {}", traceId, questionId, score);

        // Fetch existing question
        Question question = questionRepository.findByIdAndDeletedAtIsNull(questionId)
                .orElseThrow(() -> {
                    log.error("[{}] Question not found for id: {}", traceId, questionId);
                    return new ApiException(Const.QUESTION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Update score
        question.setScore(BigDecimal.valueOf(score));
        questionRepository.save(question);

        // Optional: record to history if you’re tracking changes
        log.info("[{}] Updated score for question ID {} to {}", traceId, questionId, score);

    }

}