package com.learning.progress.dto.clazz.lesson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyncClassLessonRequest {

    @NotNull(message = Const.LESSON.ID_REQUIRED, groups = Deleted.class)
    private Long id; // Null khi tạo mới, bắt buộc khi update/xóa

    @NotBlank(groups = NotDeleted.class, message = Const.LESSON.LESSON_NAME_REQUIRED)
    @Size(groups = NotDeleted.class, max = Const.LESSON.LESSON_NAME_MAX_LENGTH_VALUE, message = Const.LESSON.LESSON_NAME_MAX_LENGTH)
    private String classLessonName;

    @Size(groups = NotDeleted.class, max = 1000, message = Const.LESSON.LESSON_CONTENT_TOO_LONG_VN)
    private String classLessonContent; // Có thể null

    @NotNull(groups = NotDeleted.class, message = Const.CLASS_CHAPTER.ORDER_NUMBER_REQUIRED_VN)
    @Min(groups = NotDeleted.class, value = 1, message = Const.CLASS_CHAPTER.ORDER_NUMBER_MIN_VN)
    private Integer orderNumber;

    private boolean toBeDeleted;

    // Validation groups
    public interface Deleted {}
    public interface NotDeleted {}
}