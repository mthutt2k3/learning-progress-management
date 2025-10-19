package com.learning.progress.dto.lesson;

import jakarta.persistence.Column;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SyncLessonRequest {
    @NotNull(message = "ID bắt buộc khi xóa", groups = Deleted.class)
    private Long id; // Nullable cho lesson mới

    // Chỉ validate khi không bị xóa
    @NotBlank(message = "Tên lesson không được để trống", groups = NotDeleted.class)
    private String lessonName;

    // Chỉ validate khi không bị xóa
    @NotNull(message = "Order number không được null", groups = NotDeleted.class)
    @Min(value = 1, message = "Order number phải từ 1 trở lên", groups = NotDeleted.class)
    private Integer orderNumber;

    @Column(length = Integer.MAX_VALUE)
    private String content;

    private boolean toBeDeleted; // Cờ đánh dấu xóa

    // Validation groups
    public interface NotDeleted {}
    public interface Deleted {
    }
}