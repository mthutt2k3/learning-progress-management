package com.learning.progress.dto.syllabus;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SyncChapterRequest {
    private Long id; // Nullable cho chapter mới

    // Chỉ validate khi không bị xóa
    @NotBlank(message = "Tên chapter không được để trống", groups = NotDeleted.class)
    private String chapterName;

    // Chỉ validate khi không bị xóa
    @NotNull(message = "Order number không được null", groups = NotDeleted.class)
    @Min(value = 1, message = "Order number phải từ 1 trở lên", groups = NotDeleted.class)
    private Integer orderNumber;

    private boolean toBeDeleted; // Cờ đánh dấu xóa

    // Validation group cho các chapter không bị xóa
    public interface NotDeleted {}
}