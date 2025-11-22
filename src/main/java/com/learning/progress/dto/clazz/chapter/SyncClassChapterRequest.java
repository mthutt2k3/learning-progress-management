package com.learning.progress.dto.clazz.chapter;

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
public class SyncClassChapterRequest {

    @NotNull(message = Const.CLASS_CHAPTER.ID_REQUIRED_WHEN_DELETING_VN, groups = Deleted.class)
    private Long id; // Null khi tạo mới, bắt buộc khi update/xóa

    @NotBlank(groups = NotDeleted.class, message = Const.CLASS_CHAPTER.CHAPTER_NAME_REQUIRED_VN)
    @Size(groups = NotDeleted.class, max = 100, message = Const.CLASS_CHAPTER.CHAPTER_NAME_MAX_LENGTH_VN)
    private String classChapterName;

    @NotNull(groups = NotDeleted.class, message = Const.CLASS_CHAPTER.ORDER_NUMBER_REQUIRED_VN)
    @Min(groups = NotDeleted.class, value = 1, message = Const.CLASS_CHAPTER.ORDER_NUMBER_MIN_VN)
    private Integer orderNumber;

    private boolean toBeDeleted;

    // Validation groups
    public interface Deleted {}
    public interface NotDeleted {}
}