package com.learning.progress.dto.clazz;

import com.learning.progress.dto.syllabus.SyncLessonRequest;
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
public class SyncClassLessonRequest {

    @NotNull(message = "ID bắt buộc khi xóa", groups = Deleted.class)
    private Long id; // Null khi tạo mới, bắt buộc khi update/xóa

    @NotBlank(groups = NotDeleted.class, message = "Tên lesson không được để trống")
    @Size(groups = NotDeleted.class, max = 100, message = "Tên lesson không được vượt quá 100 ký tự")
    private String classLessonName;

    private String classLessonContent; // Có thể null

    @NotNull(groups = NotDeleted.class, message = "Thứ tự không được để trống")
    @Min(groups = NotDeleted.class, value = 1, message = "Thứ tự phải từ 1 trở lên")
    private Integer orderNumber;

    private boolean toBeDeleted;

    // Validation groups
    public interface Deleted {}
    public interface NotDeleted {}
}