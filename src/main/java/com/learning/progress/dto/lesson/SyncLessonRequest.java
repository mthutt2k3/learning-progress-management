package com.learning.progress.dto.lesson;

import com.learning.progress.common.Const;
import jakarta.persistence.Column;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SyncLessonRequest {

    // Nullable cho lesson mới, required khi delete/update
    @NotNull(message = Const.LESSON.ID_REQUIRED, groups = Deleted.class)
    private Long id;

    // Chỉ validate khi không bị xóa
    @NotBlank(message = Const.LESSON.LESSON_NAME_REQUIRED, groups = NotDeleted.class)
    @Size(max = Const.LESSON.LESSON_NAME_MAX_LENGTH_VALUE, message = Const.LESSON.LESSON_NAME_MAX_LENGTH, groups = NotDeleted.class)
    private String lessonName;

    // Chỉ validate khi không bị xóa
    @NotNull(message = Const.ORDER_NUMBER.ORDER_NUMBER_REQUIRED, groups = NotDeleted.class)
    @Min(value = 1, message = Const.ORDER_NUMBER.ORDER_NUMBER_MIN, groups = NotDeleted.class)
    private Integer orderNumber;

    @Column(length = Integer.MAX_VALUE)
    private String content;

    private boolean toBeDeleted; // Cờ đánh dấu xóa

    // Validation groups
    public interface NotDeleted {}
    public interface Deleted {}
}
