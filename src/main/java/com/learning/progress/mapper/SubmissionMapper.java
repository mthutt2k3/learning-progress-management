package com.learning.progress.mapper;

import com.learning.progress.dto.submission.SaveSubmissionRequest;
import com.learning.progress.dto.submission.SubmissionResponse;
import com.learning.progress.dto.submission.SubmissionResultDTO;
import com.learning.progress.entity.SubmissionDailyChallenge;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface SubmissionMapper {

    SubmissionDailyChallenge mapToEntity(SaveSubmissionRequest request);

}