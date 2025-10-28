package com.learning.progress.service;

import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.section.QuickBulkSectionRequest;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import com.learning.progress.dto.challenge.section.StudentSectionWithQuestionsDto;

import java.util.List;

public interface ChallengeSectionService {
    SectionWithQuestionsDto saveSection(Long challengeId, SectionWithQuestionsDto dto);
    List<SectionWithQuestionsDto> saveSectionList(Long challengeId, List<SectionWithQuestionsDto> dtos);
    SectionWithQuestionsDto getSection(Long id);
    DataResponse<List<SectionWithQuestionsDto>> listSections(Long challengeId, int page, int size, String text);
    DataResponse<List<StudentSectionWithQuestionsDto>> listSectionsWithoutAnswers(Long challengeId, int page, int size, String text);

    void bulkOrderSection(Long challengeId, List<QuickBulkSectionRequest> dto);

    void updateScoreQuestion(Long questionId, double score);

}
