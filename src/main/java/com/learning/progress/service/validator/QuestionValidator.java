package com.learning.progress.service.validator;

import com.learning.progress.common.Const;
import com.learning.progress.common.QuestionType;
import com.learning.progress.dto.challenge.section.DataItem;
import com.learning.progress.dto.challenge.section.DataContent;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.exception.ApiException;
import com.learning.progress.util.TraceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionValidator {

    private static final Set<QuestionType> QUESTION_TYPES_WITH_POSITION_ORDER = Set.of(
            QuestionType.MULTIPLE_CHOICE,
            QuestionType.MULTIPLE_SELECT,
            QuestionType.TRUE_OR_FALSE,
            QuestionType.DROPDOWN,
            QuestionType.DRAG_AND_DROP,
            QuestionType.REARRANGE
    );

    private static final Set<QuestionType> QUESTION_TYPES_WITH_POSITION_ID = Set.of(
            QuestionType.FILL_IN_THE_BLANK,
            QuestionType.DROPDOWN,
            QuestionType.DRAG_AND_DROP,
            QuestionType.REARRANGE
    );


    public void validateQuestionDto(QuestionDto dto) {
        String traceId = TraceUtil.getTraceId();
        if (dto == null) {
            log.error("[{}] {}", traceId, Const.QUESTION.NULL_OBJECT);
            throw new ApiException(Const.QUESTION.NULL_OBJECT, HttpStatus.BAD_REQUEST.value());
        }

        if (dto.getQuestionText() == null || dto.getQuestionText().isBlank()) {
            log.error("[{}] {}", traceId, Const.QUESTION.EMPTY_TEXT);
            throw new ApiException(Const.QUESTION.EMPTY_TEXT, HttpStatus.BAD_REQUEST.value());
        }

        if (dto.getScore() <= 0) {
            log.error("[{}] {}", traceId, Const.QUESTION.INVALID_SCORE);
            throw new ApiException(Const.QUESTION.INVALID_SCORE, HttpStatus.BAD_REQUEST.value());
        }

        if (dto.getQuestionType() == null || !isValidQuestionType(dto.getQuestionType())) {
            log.error("[{}] {}", traceId, Const.QUESTION.TYPE_REQUIRED);
            throw new ApiException(Const.QUESTION.TYPE_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        QuestionType questionType;
        try {
            questionType = QuestionType.valueOf(dto.getQuestionType());
        } catch (IllegalArgumentException e) {
            log.error("[{}] Invalid question type: {}", traceId, dto.getQuestionType());
            throw new ApiException("Invalid question type: " + dto.getQuestionType(), HttpStatus.BAD_REQUEST.value());
        }

        validateByQuestionType(dto, questionType);
    }

    private boolean isValidQuestionType(String questionType) {
        try {
            QuestionType.valueOf(questionType);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void validateQuestionContent(DataContent content, String traceId) {
        if (content == null || content.getData() == null || content.getData().isEmpty()) {
            log.error("[{}] {}", traceId, Const.QUESTION.EMPTY_CONTENT);
            throw new ApiException(Const.QUESTION.EMPTY_CONTENT, HttpStatus.BAD_REQUEST.value());
        }
    }

    private void validateByQuestionType(QuestionDto dto, QuestionType questionType) {
        String traceId = TraceUtil.getTraceId();
        List<DataItem> dataItems = dto.getContent() != null ? dto.getContent().getData() : Collections.emptyList();

        Set<String> seenIds = new HashSet<>();
        for (DataItem item : dataItems) {
            if (item.getId() != null && !item.getId().isBlank()) {
                if (!seenIds.add(item.getId())) {
                    log.error("[{}] Duplicate data item id '{}' found in question {}", traceId, item.getId(), dto.getId());
                    throw new ApiException(
                            String.format("Duplicate data item id '%s' found in question %d", item.getId(), dto.getId()),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }
            }
        }

        // Validate positionOrder for applicable question types
        if (QUESTION_TYPES_WITH_POSITION_ORDER.contains(questionType) && !dataItems.isEmpty()) {
            validatePositionOrder(dataItems, questionType, traceId);
        }

        switch (questionType) {
            case MULTIPLE_CHOICE:
                validateMultipleChoice(dataItems, traceId);
                break;
            case MULTIPLE_SELECT:
                validateMultipleSelect(dataItems, traceId);
                break;
            case TRUE_OR_FALSE:
                validateTrueOrFalse(dataItems, traceId);
                break;
            case FILL_IN_THE_BLANK:
                validateFillInTheBlank(dto, dataItems, traceId);
                break;
            case DROPDOWN:
                validateDropdown(dto, dataItems, traceId);
                break;
            case DRAG_AND_DROP:
                validateDragAndDrop(dto, dataItems, traceId);
                break;
            case REARRANGE:
                validateRearrange(dto, dataItems, traceId);
                break;
            case REWRITE:
                validateRewrite(dataItems, traceId);
                break;
            default:
                log.error("[{}] Invalid question type: {}", traceId, questionType);
                throw new ApiException("Invalid question type: " + questionType, HttpStatus.BAD_REQUEST.value());
        }
    }

    private void validatePositionOrder(List<DataItem> dataItems, QuestionType questionType, String traceId) {
        Set<Integer> positionOrders = dataItems.stream()
                .map(DataItem::getPositionOrder)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int expectedSize = dataItems.size();
        Set<Integer> expectedOrders = IntStream.rangeClosed(1, expectedSize).boxed().collect(Collectors.toSet());

        if (positionOrders.size() != expectedSize || !positionOrders.equals(expectedOrders)) {
            log.error("[{}] Position orders must be sequential from 1 to {} for {}. Found: {}", traceId, expectedSize, questionType, positionOrders);
            throw new ApiException(
                    String.format("Position orders must be sequential from 1 to %d for %s. Found: %s", expectedSize, questionType, positionOrders),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }

    private void validateMultipleChoice(List<DataItem> dataItems, String traceId) {
        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != 1) {
            log.error("[{}] MULTIPLE_CHOICE must have exactly one correct answer. Found: {}", traceId, correctCount);
            throw new ApiException("MULTIPLE_CHOICE must have exactly one correct answer", HttpStatus.BAD_REQUEST.value());
        }
        if (dataItems.size() < 2) {
            log.error("[{}] MULTIPLE_CHOICE must have at least 2 options. Found: {}", traceId, dataItems.size());
            throw new ApiException("MULTIPLE_CHOICE must have at least 2 options", HttpStatus.BAD_REQUEST.value());
        }
    }

    private void validateMultipleSelect(List<DataItem> dataItems, String traceId) {
        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount < 1) {
            log.error("[{}] MULTIPLE_SELECT must have at least one correct answer. Found: {}", traceId, correctCount);
            throw new ApiException("MULTIPLE_SELECT must have at least one correct answer", HttpStatus.BAD_REQUEST.value());
        }
        if (dataItems.size() < 2) {
            log.error("[{}] MULTIPLE_SELECT must have at least 2 options. Found: {}", traceId, dataItems.size());
            throw new ApiException("MULTIPLE_SELECT must have at least 2 options", HttpStatus.BAD_REQUEST.value());
        }
    }

    private void validateTrueOrFalse(List<DataItem> dataItems, String traceId) {
        if (dataItems.size() != 2) {
            log.error("[{}] TRUE_OR_FALSE must have exactly 2 options. Found: {}", traceId, dataItems.size());
            throw new ApiException("TRUE_OR_FALSE must have exactly 2 options", HttpStatus.BAD_REQUEST.value());
        }
        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != 1) {
            log.error("[{}] TRUE_OR_FALSE must have exactly one correct answer. Found: {}", traceId, correctCount);
            throw new ApiException("TRUE_OR_FALSE must have exactly one correct answer", HttpStatus.BAD_REQUEST.value());
        }
        Set<String> values = dataItems.stream().map(DataItem::getValue).map(String::toLowerCase).collect(Collectors.toSet());
        if (!values.contains("true") || !values.contains("false")) {
            log.error("[{}] TRUE_OR_FALSE must have 'True' and 'False' options. Found: {}", traceId, values);
            throw new ApiException("TRUE_OR_FALSE must have 'True' and 'False' options", HttpStatus.BAD_REQUEST.value());
        }
    }


    private void validateFillInTheBlank(QuestionDto dto, List<DataItem> dataItems, String traceId) {
        Set<String> placeholders = extractPlaceholders(dto.getQuestionText(), traceId);
        if (placeholders.isEmpty()) {
            log.error("[{}] FILL_IN_THE_BLANK must have at least one placeholder", traceId);
            throw new ApiException("FILL_IN_THE_BLANK must have at least one placeholder", HttpStatus.BAD_REQUEST.value());
        }

        Set<String> dataPositionIds = dataItems.stream()
                .map(DataItem::getPositionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (String placeholder : placeholders) {
            String cleanPlaceholder = placeholder.startsWith("pos_") ? placeholder.substring(4) : placeholder;
            if (!dataPositionIds.contains(cleanPlaceholder)) {
                String msg = String.format("FILL_IN_THE_BLANK: Missing positionId for placeholder: %s", placeholder);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
        }

        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != placeholders.size()) {
            log.error("[{}] FILL_IN_THE_BLANK must have exactly one correct answer per placeholder. Expected: {}, Found: {}", traceId, placeholders.size(), correctCount);
            throw new ApiException("FILL_IN_THE_BLANK must have exactly one correct answer per placeholder", HttpStatus.BAD_REQUEST.value());
        }
    }

    private void validateDropdown(QuestionDto dto, List<DataItem> dataItems, String traceId) {
        Set<String> placeholders = extractPlaceholders(dto.getQuestionText(), traceId);
        if (placeholders.isEmpty()) {
            log.error("[{}] DROPDOWN must have at least one placeholder", traceId);
            throw new ApiException("DROPDOWN must have at least one placeholder", HttpStatus.BAD_REQUEST.value());
        }

        Map<String, List<DataItem>> itemsByPositionId = dataItems.stream()
                .filter(item -> item.getPositionId() != null)
                .collect(Collectors.groupingBy(DataItem::getPositionId));

        for (String placeholder : placeholders) {
            String cleanPlaceholder = placeholder.startsWith("pos_") ? placeholder.substring(4) : placeholder;
            List<DataItem> items = itemsByPositionId.getOrDefault(cleanPlaceholder, Collections.emptyList());
            if (items.size() < 2) {
                log.error("[{}] DROPDOWN must have at least 2 options per placeholder: {}", traceId, cleanPlaceholder);
                throw new ApiException("DROPDOWN must have at least 2 options per placeholder: " + cleanPlaceholder, HttpStatus.BAD_REQUEST.value());
            }
            long correctCount = items.stream().filter(DataItem::isCorrect).count();
            if (correctCount != 1) {
                log.error("[{}] DROPDOWN must have exactly one correct answer per placeholder: {}. Found: {}", traceId, cleanPlaceholder, correctCount);
                throw new ApiException("DROPDOWN must have exactly one correct answer per placeholder: " + cleanPlaceholder, HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateDragAndDrop(QuestionDto dto, List<DataItem> dataItems, String traceId) {
        Set<String> placeholders = extractPlaceholders(dto.getQuestionText(), traceId);
        if (placeholders.isEmpty()) {
            log.error("[{}] DRAG_AND_DROP must have at least one placeholder", traceId);
            throw new ApiException("DRAG_AND_DROP must have at least one placeholder", HttpStatus.BAD_REQUEST.value());
        }

        Map<String, List<DataItem>> itemsByPositionId = dataItems.stream()
                .filter(item -> item.getPositionId() != null)
                .collect(Collectors.groupingBy(DataItem::getPositionId));

        for (String placeholder : placeholders) {
            String cleanPlaceholder = placeholder.startsWith("pos_") ? placeholder.substring(4) : placeholder;
            List<DataItem> items = itemsByPositionId.getOrDefault(cleanPlaceholder, Collections.emptyList());
            if (items.isEmpty()) {
                log.error("[{}] DRAG_AND_DROP must have at least one option per placeholder: {}", traceId, cleanPlaceholder);
                throw new ApiException("DRAG_AND_DROP must have at least one option per placeholder: " + cleanPlaceholder, HttpStatus.BAD_REQUEST.value());
            }
            long correctCount = items.stream().filter(DataItem::isCorrect).count();
            if (correctCount != 1) {
                log.error("[{}] DRAG_AND_DROP must have exactly one correct answer per placeholder: {}. Found: {}", traceId, cleanPlaceholder, correctCount);
                throw new ApiException("DRAG_AND_DROP must have exactly one correct answer per placeholder: " + cleanPlaceholder, HttpStatus.BAD_REQUEST.value());
            }
        }

        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != placeholders.size()) {
            log.error("[{}] DRAG_AND_DROP must have exactly one correct answer per placeholder. Expected: {}, Found: {}", traceId, placeholders.size(), correctCount);
            throw new ApiException("DRAG_AND_DROP must have exactly one correct answer per placeholder", HttpStatus.BAD_REQUEST.value());
        }

        int totalOptions = dataItems.size();
        if (totalOptions < placeholders.size()) {
            log.error("[{}] DRAG_AND_DROP must have at least as many options as placeholders. Options: {}, Placeholders: {}", traceId, totalOptions, placeholders.size());
            throw new ApiException("DRAG_AND_DROP must have at least as many options as placeholders", HttpStatus.BAD_REQUEST.value());
        }
    }

    private void validateRearrange(QuestionDto dto, List<DataItem> dataItems, String traceId) {
        Set<String> placeholders = extractPlaceholders(dto.getQuestionText(), traceId);
        if (placeholders.isEmpty()) {
            log.error("[{}] REARRANGE must have at least one placeholder", traceId);
            throw new ApiException("REARRANGE must have at least one placeholder", HttpStatus.BAD_REQUEST.value());
        }

        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != placeholders.size()) {
            log.error("[{}] REARRANGE must have exactly one correct answer per placeholder. Expected: {}, Found: {}", traceId, placeholders.size(), correctCount);
            throw new ApiException("REARRANGE must have exactly one correct answer per placeholder", HttpStatus.BAD_REQUEST.value());
        }

        Set<String> dataPositionIds = dataItems.stream()
                .map(DataItem::getPositionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (String placeholder : placeholders) {
            String cleanPlaceholder = placeholder.startsWith("pos_") ? placeholder.substring(4) : placeholder;
            if (!dataPositionIds.contains(cleanPlaceholder)) {
                String msg = String.format("REARRANGE: Missing positionId for placeholder: %s", placeholder);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateRewrite(List<DataItem> dataItems, String traceId) {
        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount < 1) {
            log.error("[{}] REWRITE must have at least one correct answer. Found: {}", traceId, correctCount);
            throw new ApiException("REWRITE must have at least one correct answer", HttpStatus.BAD_REQUEST.value());
        }
    }

    private Set<String> extractPlaceholders(String questionText, String traceId) {
        Set<String> placeholders = new HashSet<>();
        Matcher matcher = Const.QUESTION.POSITION_PATTERN.matcher(questionText);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        log.debug("[{}] Extracted placeholders: {}", traceId, placeholders);
        return placeholders;
    }
}