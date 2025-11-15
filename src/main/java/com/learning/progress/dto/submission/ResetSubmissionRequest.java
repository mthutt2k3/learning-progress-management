package com.learning.progress.dto.submission;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetSubmissionRequest {

    @NotEmpty(message = "Danh sách submission ID không được để trống")
    private List<Long> submissionIds;

    @NotNull(message = "Thời gian bắt đầu mới không được để trống")
    private OffsetDateTime newStartDate;

    @NotNull(message = "Thời gian kết thúc mới không được để trống")
    private OffsetDateTime newEndDate;

}