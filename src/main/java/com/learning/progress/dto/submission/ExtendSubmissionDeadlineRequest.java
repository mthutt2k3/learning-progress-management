package com.learning.progress.dto.submission;

import jakarta.validation.constraints.FutureOrPresent;
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
public class ExtendSubmissionDeadlineRequest {

    @NotEmpty(message = "Danh sách submission ID không được để trống")
    private List<Long> submissionIds;

    @NotNull(message = "Thời gian gia hạn không được để trống")
    @FutureOrPresent(message = "Thời gian gia hạn phải từ hiện tại trở đi")
    private OffsetDateTime newExpiredAt;

}