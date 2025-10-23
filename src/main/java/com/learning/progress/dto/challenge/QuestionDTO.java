package com.learning.progress.dto.challenge;

import com.learning.progress.common.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
public class QuestionDTO {
    private Long id;

    @NotNull(message = "SectionDto ID is required")
    private Long sectionId;

    @NotBlank(message = "QuestionDto text is required")
    private String questionText;

    @NotNull(message = "QuestionDto type is required")
    private QuestionType questionType;

    private Map<String, Object> questionContentJson;

    @PositiveOrZero(message = "Order number must be non-negative")
    private Integer orderNumber;

    @PositiveOrZero(message = "Score must be non-negative")
    private BigDecimal score;

    private String createdBy;

    private OffsetDateTime createdAt;

    private String updatedBy;

    private OffsetDateTime updatedAt;
}