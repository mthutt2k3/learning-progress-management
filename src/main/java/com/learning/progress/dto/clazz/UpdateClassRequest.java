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

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateClassRequest {
    @NotBlank(message = "Tên lớp không được để trống")
    @Size(max = 50, message = "Tên lớp không được vượt quá 50 ký tự")
    private String className;

    private String avatarUrl;

    @NotNull(message = Const.CLASS.START_DATE_REQUIRED)
    private OffsetDateTime startDate;

    @NotNull(message = Const.CLASS.END_DATE_REQUIRED)
    private OffsetDateTime endDate;
}