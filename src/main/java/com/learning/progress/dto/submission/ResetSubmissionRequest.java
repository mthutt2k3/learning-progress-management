package com.learning.progress.dto.submission;

import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.OffsetDateTime;
import java.util.List;

@SuperBuilder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetSubmissionRequest {

    @NotEmpty(message = Const.SUBMISSION.SUBMISSION_IDS_REQUIRED_VN)
    private List<Long> submissionIds;

    @NotNull(message = Const.SUBMISSION.NEW_START_DATE_REQUIRED_VN)
    private OffsetDateTime newStartDate;

    @NotNull(message = Const.SUBMISSION.NEW_END_DATE_REQUIRED_VN)
    private OffsetDateTime newEndDate;

}