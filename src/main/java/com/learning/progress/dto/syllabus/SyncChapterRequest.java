package com.learning.progress.dto.syllabus;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SyncChapterRequest {
    @NotNull(message = "ID bắt buộc khi xóa", groups = Deleted.class)
    private Long id; // Required cho delete/update, null cho new

    // Chỉ validate khi !toBeDeleted
    @NotBlank(message = "Tên chapter không được để trống", groups = NotDeleted.class)
    private String chapterName;

    // Chỉ validate khi !toBeDeleted
    @NotNull(message = "Order number không được null", groups = NotDeleted.class)
    @Min(value = 1, message = "Order number phải từ 1 trở lên", groups = NotDeleted.class)
    private Integer orderNumber;

    private boolean toBeDeleted; // true = chỉ cần id để delete

    // Validation cho non-deleted items
    public interface NotDeleted {}

    // Validation cho deleted items
    public interface Deleted {}
}