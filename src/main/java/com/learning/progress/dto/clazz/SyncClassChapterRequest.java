package com.learning.progress.dto.clazz;

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
public class SyncClassChapterRequest {

    private Long id; // Null khi tạo mới, bắt buộc khi update/xóa

    @NotBlank(groups = NotDeleted.class, message = "Tên chapter không được để trống")
    @Size(groups = NotDeleted.class, max = 100, message = "Tên chapter không được vượt quá 100 ký tự")
    private String classChapterName;

    private String classChapterContent; // Có thể null

    @NotNull(groups = NotDeleted.class, message = "Thứ tự không được để trống")
    @Min(groups = NotDeleted.class, value = 1, message = "Thứ tự phải từ 1 trở lên")
    private Integer orderNumber;

    private boolean toBeDeleted;

    // Validation groups
    public interface Deleted {}
    public interface NotDeleted {}
}