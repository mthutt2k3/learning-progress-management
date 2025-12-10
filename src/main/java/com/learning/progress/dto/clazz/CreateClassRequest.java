package com.learning.progress.dto.clazz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateClassRequest {
    @NotBlank(message = Const.CLASS.CLASS_NAME_REQUIRED)
    @Size(max = Const.CLASS.CLASS_NAME_MAX_LENGTH_VALUE, message = Const.CLASS.CLASS_NAME_MAX_LENGTH)
    private String className;

    @NotNull(message = Const.SYLLABUS.ID_REQUIRED)
    private Long syllabusId;

    private String avatarUrl; // Có thể null

    @NotNull(message = Const.CLASS.START_DATE_REQUIRED)
    private OffsetDateTime startDate;

    @NotNull(message = Const.CLASS.END_DATE_REQUIRED)
    private OffsetDateTime endDate;
}