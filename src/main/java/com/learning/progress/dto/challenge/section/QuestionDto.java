package com.learning.progress.dto.challenge.section;

import com.learning.progress.common.Const;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDto {
    @NotNull(message = Const.QUESTION.ID_REQUIRED, groups = Deleted.class)
    private Long id;
    @NotBlank(message = Const.QUESTION.EMPTY_TEXT, groups = NotDeleted.class)
    private String questionText;
    @NotNull(message = Const.ORDER_NUMBER.ORDER_NUMBER_REQUIRED, groups = NotDeleted.class)
    @Min(value = 1, message = Const.ORDER_NUMBER.ORDER_NUMBER_MIN, groups = NotDeleted.class)
    private int orderNumber;
    @Min(value = 0, message = Const.QUESTION.INVALID_SCORE, groups = NotDeleted.class)
    private double score;
    @NotBlank(message = Const.QUESTION.TYPE_REQUIRED, groups = NotDeleted.class)
    private String questionType;
    @NotNull(message = Const.QUESTION.CONTENT_REQUIRED, groups = NotDeleted.class)
    private DataContent content;
    private boolean toBeDeleted;

    public interface NotDeleted {}
    public interface Deleted {}
}