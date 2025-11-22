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

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionValidator {

    private static final Set<QuestionType> QUESTION_TYPES_WITH_POSITION_ORDER = Set.of(
            QuestionType.MULTIPLE_CHOICE,
            QuestionType.MULTIPLE_SELECT,
            QuestionType.TRUE_OR_FALSE
    );

    private static final Set<QuestionType> QUESTION_TYPES_WITH_POSITION_ID = Set.of(
            QuestionType.FILL_IN_THE_BLANK,
            QuestionType.DROPDOWN,
            QuestionType.DRAG_AND_DROP,
            QuestionType.REARRANGE
    );


    public void validateQuestionDto(QuestionDto dto) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] validateQuestionDto enter dtoId={} type={} traceId={}", traceId, dto != null ? dto.getId() : null, dto != null ? dto.getQuestionType() : null, traceId);

        if (dto == null) {
            log.error("[{}] {}", traceId, Const.QUESTION.NULL_OBJECT);
            throw new ApiException(Const.QUESTION.NULL_OBJECT, HttpStatus.BAD_REQUEST.value());
        }

        if (dto.getQuestionText() == null || dto.getQuestionText().isBlank()) {
            log.error("[{}] {}", traceId, Const.QUESTION.EMPTY_TEXT);
            throw new ApiException(Const.QUESTION.EMPTY_TEXT, HttpStatus.BAD_REQUEST.value());
        }

        if (dto.getWeight() <= 0) {
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
            String msg = String.format(Const.QUESTION.INVALID_QUESTION_TYPE, dto.getQuestionType());
            log.error("[{}] {}", traceId, msg);
            throw new ApiException(String.format(Const.QUESTION.VALIDATION_FAILED, msg), HttpStatus.BAD_REQUEST.value());
        }

        validateByQuestionType(dto, questionType);
        log.info("[{}] validateQuestionDto exit dtoId={} traceId={}", traceId, dto.getId(), traceId);
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
        log.debug("[{}] validateByQuestionType start dtoId={} type={}", traceId, dto.getId(), questionType);

        List<DataItem> dataItems = dto.getContent() != null ? dto.getContent().getData() : Collections.emptyList();

        Set<String> seenIds = new HashSet<>();
        for (DataItem item : dataItems) {
            if (item.getId() != null && !item.getId().isBlank()) {
                if (!seenIds.add(item.getId())) {
                    String err = String.format(Const.QUESTION.DUPLICATE_DATA_ITEM_ID, item.getId(), String.valueOf(dto.getId()));
                    log.error("[{}] {}", traceId, err);
                    throw new ApiException(err, HttpStatus.BAD_REQUEST.value());
                }
            }
        }

        if (QUESTION_TYPES_WITH_POSITION_ID.contains(questionType) && !dataItems.isEmpty()) {
            validatePositionOrderByPosisionId(dto, dataItems, questionType, traceId);
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
            case WRITING:
                break;
            case SPEAKING:
                break;

            default:
                log.error("[{}] Invalid question type: {}", traceId, questionType);
                throw new ApiException(String.format(Const.QUESTION.VALIDATION_FAILED, "Invalid question type: " + questionType), HttpStatus.BAD_REQUEST.value());
        }
        log.debug("[{}] validateByQuestionType success dtoId={} type={}", traceId, dto.getId(), questionType);
    }

    private void validatePositionOrderByPosisionId(QuestionDto dto, List<DataItem> dataItems, QuestionType questionType, String traceId) {
        // 1️⃣ Lấy tất cả placeholder từ questionText
        Set<String> placeholders = extractPlaceholders(dto.getQuestionText(), traceId);

        if (placeholders.isEmpty()) {
            log.error("[{}] {}", traceId, Const.QUESTION.NO_PLACEHOLDERS_FOUND);
            throw new ApiException(Const.QUESTION.NO_PLACEHOLDERS_FOUND, HttpStatus.BAD_REQUEST.value());
        }

        // 2️⃣ Gom dataItems theo positionId (VD: "1", "2", "A", ...)
        Map<String, List<DataItem>> groupedByPos = dataItems.stream()
                .filter(item -> item.getPositionId() != null)
                .collect(Collectors.groupingBy(DataItem::getPositionId));

        // 3️⃣ Validate từng placeholder theo group riêng
        for (String placeholder : placeholders) {
            // Loại bỏ prefix "pos_" nếu có
            String cleanPos = placeholder.startsWith("pos_") ? placeholder.substring(4) : placeholder;

            List<DataItem> itemsForPos = groupedByPos.get(cleanPos);
            if (itemsForPos == null || itemsForPos.isEmpty()) {
                String msg = String.format(Const.QUESTION.NO_DATA_ITEMS_FOR_PLACEHOLDER, placeholder);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
        }

        log.info("[{}] Position orders validated successfully for {}", traceId, questionType);
    }


    private void validateMultipleChoice(List<DataItem> dataItems, String traceId) {
        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != 1) {
            log.error("[{}] {}. Found: {}", traceId, Const.QUESTION.MULTIPLE_CHOICE_ONE_CORRECT, correctCount);
            throw new ApiException(Const.QUESTION.MULTIPLE_CHOICE_ONE_CORRECT, HttpStatus.BAD_REQUEST.value());
        }
        if (dataItems.size() < 2) {
            log.error("[{}] {}. Found: {}", traceId, Const.QUESTION.MULTIPLE_CHOICE_MIN_OPTIONS, dataItems.size());
            throw new ApiException(Const.QUESTION.MULTIPLE_CHOICE_MIN_OPTIONS, HttpStatus.BAD_REQUEST.value());
        }
    }

    private void validateMultipleSelect(List<DataItem> dataItems, String traceId) {
        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount < 1) {
            log.error("[{}] {}. Found: {}", traceId, Const.QUESTION.MULTIPLE_SELECT_MIN_CORRECT, correctCount);
            throw new ApiException(Const.QUESTION.MULTIPLE_SELECT_MIN_CORRECT, HttpStatus.BAD_REQUEST.value());
        }
        if (dataItems.size() < 2) {
            log.error("[{}] {}. Found: {}", traceId, Const.QUESTION.MULTIPLE_SELECT_MIN_OPTIONS, dataItems.size());
            throw new ApiException(Const.QUESTION.MULTIPLE_SELECT_MIN_OPTIONS, HttpStatus.BAD_REQUEST.value());
        }
    }

    private void validateTrueOrFalse(List<DataItem> dataItems, String traceId) {
        if (dataItems.size() != 2) {
            log.error("[{}] {}. Found: {}", traceId, Const.QUESTION.TRUE_FALSE_TWO_OPTIONS, dataItems.size());
            throw new ApiException(Const.QUESTION.TRUE_FALSE_TWO_OPTIONS, HttpStatus.BAD_REQUEST.value());
        }
        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != 1) {
            log.error("[{}] {}. Found: {}", traceId, Const.QUESTION.TRUE_FALSE_ONE_CORRECT, correctCount);
            throw new ApiException(Const.QUESTION.TRUE_FALSE_ONE_CORRECT, HttpStatus.BAD_REQUEST.value());
        }
        Set<String> values = dataItems.stream().map(DataItem::getValue).map(v -> v == null ? "" : v.toLowerCase()).collect(Collectors.toSet());
        if (!values.contains("true") || !values.contains("false")) {
            log.error("[{}] {}. Found: {}", traceId, Const.QUESTION.TRUE_FALSE_OPTIONS_REQUIRED, values);
            throw new ApiException(Const.QUESTION.TRUE_FALSE_OPTIONS_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }
    }


    private void validateFillInTheBlank(QuestionDto dto, List<DataItem> dataItems, String traceId) {
        Set<String> placeholders = extractPlaceholders(dto.getQuestionText(), traceId);
        if (placeholders.isEmpty()) {
            log.error("[{}] {}", traceId, Const.QUESTION.FILL_IN_THE_BLANK_PLACEHOLDER_REQUIRED);
            throw new ApiException(Const.QUESTION.FILL_IN_THE_BLANK_PLACEHOLDER_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        validatePlaceHolder(traceId, dto, placeholders);

        Set<String> dataPositionIds = dataItems.stream()
                .map(DataItem::getPositionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (String placeholder : placeholders) {
            String cleanPlaceholder = placeholder.startsWith("pos_") ? placeholder.substring(4) : placeholder;
            if (!dataPositionIds.contains(cleanPlaceholder)) {
                String msg = String.format(Const.QUESTION.FILL_IN_THE_BLANK_MISSING_POSITION_ID, placeholder);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
        }

        Map<String, List<DataItem>> groupedByPos = dataItems.stream()
                .collect(Collectors.groupingBy(DataItem::getPositionId));

        for (String placeholder : placeholders) {
            String cleanPlaceholder = placeholder.startsWith("pos_") ? placeholder.substring(4) : placeholder;
            List<DataItem> itemsForPos = groupedByPos.getOrDefault(cleanPlaceholder, Collections.emptyList());

            long correctCount = itemsForPos.stream().filter(DataItem::isCorrect).count();
            if (correctCount == 0) {
                String msg = String.format(Const.QUESTION.FILL_IN_THE_BLANK_PLACEHOLDER_NEEDS_CORRECT, placeholder);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateDropdown(QuestionDto dto, List<DataItem> dataItems, String traceId) {
        Set<String> placeholders = extractPlaceholders(dto.getQuestionText(), traceId);
        if (placeholders.isEmpty()) {
            log.error("[{}] {}", traceId, Const.QUESTION.NO_PLACEHOLDERS_FOUND);
            throw new ApiException(Const.QUESTION.NO_PLACEHOLDERS_FOUND, HttpStatus.BAD_REQUEST.value());
        }

        validatePlaceHolder(traceId, dto, placeholders);

        Map<String, List<DataItem>> itemsByPositionId = dataItems.stream()
                .filter(item -> item.getPositionId() != null)
                .collect(Collectors.groupingBy(DataItem::getPositionId));

        for (String placeholder : placeholders) {
            String cleanPlaceholder = placeholder.startsWith("pos_") ? placeholder.substring(4) : placeholder;
            List<DataItem> items = itemsByPositionId.getOrDefault(cleanPlaceholder, Collections.emptyList());
            if (items.size() < 2) {
                String msg = String.format(Const.QUESTION.DROPDOWN_MIN_OPTIONS_PER_PLACEHOLDER, cleanPlaceholder);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
            long correctCount = items.stream().filter(DataItem::isCorrect).count();
            if (correctCount != 1) {
                String msg = String.format(Const.QUESTION.DROPDOWN_SINGLE_CORRECT_PER_PLACEHOLDER, cleanPlaceholder);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateDragAndDrop(QuestionDto dto, List<DataItem> dataItems, String traceId) {
        Set<String> placeholders = extractPlaceholders(dto.getQuestionText(), traceId);
        if (placeholders.isEmpty()) {
            log.error("[{}] {}", traceId, Const.QUESTION.DRAG_AND_DROP_PLACEHOLDER_REQUIRED);
            throw new ApiException(Const.QUESTION.DRAG_AND_DROP_PLACEHOLDER_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        validatePlaceHolder(traceId, dto, placeholders);

        Map<String, List<DataItem>> itemsByPositionId = dataItems.stream()
                .filter(item -> item.getPositionId() != null)
                .collect(Collectors.groupingBy(DataItem::getPositionId));

        for (String placeholder : placeholders) {
            String cleanPlaceholder = placeholder.startsWith("pos_") ? placeholder.substring(4) : placeholder;
            List<DataItem> items = itemsByPositionId.getOrDefault(cleanPlaceholder, Collections.emptyList());
            if (items.isEmpty()) {
                String msg = String.format(Const.QUESTION.DRAG_AND_DROP_MIN_OPTIONS_PER_PLACEHOLDER, cleanPlaceholder);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
            long correctCount = items.stream().filter(DataItem::isCorrect).count();
            if (correctCount != 1) {
                String msg = String.format(Const.QUESTION.DRAG_AND_DROP_SINGLE_CORRECT_PER_PLACEHOLDER, cleanPlaceholder);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
        }

        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != placeholders.size()) {
            log.error("[{}] {}. Expected: {}, Found: {}", traceId, Const.QUESTION.DRAG_AND_DROP_CORRECT_COUNT_MISMATCH, placeholders.size(), correctCount);
            throw new ApiException(Const.QUESTION.DRAG_AND_DROP_CORRECT_COUNT_MISMATCH, HttpStatus.BAD_REQUEST.value());
        }

        int totalOptions = dataItems.size();
        if (totalOptions < placeholders.size()) {
            String msg = String.format(Const.QUESTION.DRAG_AND_DROP_OPTIONS_MIN_FMT, totalOptions, placeholders.size());
            log.error("[{}] {}", traceId, msg);
            throw new ApiException(String.format(Const.QUESTION.DRAG_AND_DROP_OPTIONS_MIN), HttpStatus.BAD_REQUEST.value());
        }
    }

    private void validateRearrange(QuestionDto dto, List<DataItem> dataItems, String traceId) {
        Set<String> placeholders = extractPlaceholders(dto.getQuestionText(), traceId);
        if (placeholders.isEmpty()) {
            log.error("[{}] {}", traceId, Const.QUESTION.REARRANGE_PLACEHOLDER_REQUIRED);
            throw new ApiException(Const.QUESTION.REARRANGE_PLACEHOLDER_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        validatePlaceHolder(traceId, dto, placeholders);

        boolean hasFalse = dataItems.stream().anyMatch(item -> !item.isCorrect());
        if (hasFalse) {
            log.error("[{}] {}", traceId, Const.QUESTION.REARRANGE_NO_FALSE_ANSWERS);
            throw new ApiException(Const.QUESTION.REARRANGE_NO_FALSE_ANSWERS, HttpStatus.BAD_REQUEST.value());
        }

        // ✅ Mỗi placeholder phải có đúng 1 item, và item đó phải là correct
        Map<String, List<DataItem>> groupedByPos = dataItems.stream()
                .collect(Collectors.groupingBy(DataItem::getPositionId));

        for (String placeholder : placeholders) {
            String cleanPlaceholder = placeholder.startsWith("pos_") ? placeholder.substring(4) : placeholder;
            List<DataItem> items = groupedByPos.getOrDefault(cleanPlaceholder, Collections.emptyList());

            if (items.isEmpty()) {
                String msg = String.format(Const.QUESTION.REARRANGE_MISSING_DATA_FOR_PLACEHOLDER, placeholder);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }

            if (items.size() > 1) {
                String msg = String.format(Const.QUESTION.REARRANGE_MUST_ONE_PER_PLACEHOLDER, placeholder);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }

            DataItem item = items.get(0);
            if (!item.isCorrect()) {
                String msg = String.format(Const.QUESTION.REARRANGE_PLACEHOLDER_MUST_BE_CORRECT, placeholder);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
        }

        if (dataItems.size() != placeholders.size()) {
            log.error("[{}] REARRANGE dataItems count mismatch. Expected: {}, Found: {}", traceId, placeholders.size(), dataItems.size());
            throw new ApiException(Const.QUESTION.REARRANGE_MUST_ONE_PER_PLACEHOLDER, HttpStatus.BAD_REQUEST.value());
        }
    }

    private void validateRewrite(List<DataItem> dataItems, String traceId) {
        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount < 1) {
            log.error("[{}] {}. Found: {}", traceId, Const.QUESTION.REWRITE_MIN_CORRECT, correctCount);
            throw new ApiException(Const.QUESTION.REWRITE_MIN_CORRECT, HttpStatus.BAD_REQUEST.value());
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

    private static void validatePlaceHolder(String traceId, QuestionDto dto, Set<String> placeholders) {
        String questionText = dto.getQuestionText();
        if (questionText == null || questionText.isBlank()) {
            String msg = Const.QUESTION.QUESTION_TEXT_PLACEHOLDERS_REQUIRED;
            log.error("[{}] {}", traceId, msg);
            throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
        }

        if (placeholders == null || placeholders.isEmpty()) {
            String msg = Const.QUESTION.NO_PLACEHOLDERS_FOUND;
            log.error("[{}] {}", traceId, msg);
            throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
        }

        Set<String> seen = new HashSet<>();

        for (String placeholder : placeholders) {
            // 1️⃣ Kiểm tra format
            if (!placeholder.startsWith("pos_")) {
                String msg = String.format(Const.QUESTION.REARRANGE_PLACEHOLDER_FORMAT, placeholder);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }

            // 2️⃣ Check trùng trong danh sách placeholders
            if (!seen.add(placeholder)) {
                String msg = String.format(Const.QUESTION.REARRANGE_DUPLICATE_PLACEHOLDER, placeholder);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }

            // 3️⃣ Check số lần xuất hiện trong questionText
            int count = countOccurrences(questionText, "[[" + placeholder + "]]");
            if (count > 1) {
                String msg = String.format(Const.QUESTION.REARRANGE_PLACEHOLDER_OCCURRENCE, placeholder, count);
                log.error("[{}] {}", traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
        }

        log.info("[{}] Placeholders validated successfully for dto.id={}: {}", traceId, dto.getId(), placeholders);
    }

    private static int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

}
