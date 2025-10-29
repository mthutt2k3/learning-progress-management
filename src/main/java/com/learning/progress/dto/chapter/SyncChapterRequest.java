package com.learning.progress.dto.chapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyncChapterRequest {
    @NotNull(message = Const.CHAPTER.ID_REQUIRED, groups = Deleted.class)
    private Long id; // Required cho delete/update, null cho new

    // Chỉ validate khi !toBeDeleted
    @NotBlank(message = Const.CHAPTER.CHAPTER_NAME_REQUIRED, groups = NotDeleted.class)
    @Size(max = Const.CHAPTER.CHAPTER_NAME_MAX_LENGTH_VALUE, message = Const.CHAPTER.CHAPTER_NAME_MAX_LENGTH, groups = NotDeleted.class)
    private String chapterName;

    // Chỉ validate khi !toBeDeleted
    @NotNull(message = Const.ORDER_NUMBER.ORDER_NUMBER_REQUIRED , groups = NotDeleted.class)
    @Min(value = 1, message = Const.ORDER_NUMBER.ORDER_NUMBER_MIN, groups = NotDeleted.class)
    private Integer orderNumber;

    private boolean toBeDeleted; // true = chỉ cần id để delete

    // Validation cho non-deleted items
    public interface NotDeleted {}

    // Validation cho deleted items
    public interface Deleted {}
}