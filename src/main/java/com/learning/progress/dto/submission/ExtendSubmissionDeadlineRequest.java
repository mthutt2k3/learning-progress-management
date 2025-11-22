package com.learning.progress.dto.submission;

import com.learning.progress.common.Const;
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

    @NotEmpty(message = Const.SUBMISSION.SUBMISSION_IDS_REQUIRED_VN)
    private List<Long> submissionIds;

    @NotNull(message = Const.SUBMISSION.NEW_EXPIRED_AT_REQUIRED_VN)
    @FutureOrPresent(message = Const.SUBMISSION.NEW_EXPIRED_AT_FUTURE_VN)
    private OffsetDateTime newExpiredAt;

}