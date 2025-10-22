package com.learning.progress.service;

import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;

import java.util.List;

public interface ChallengeSectionService {
    SectionWithQuestionsDto createSection(Long challengeId, SectionWithQuestionsDto dto);
    SectionWithQuestionsDto getSection(Long id);
    SectionWithQuestionsDto updateSection(Long id, SectionWithQuestionsDto dto);
    void deleteSection(Long id);
    DataResponse<List<SectionWithQuestionsDto>> listSections(Long challengeId, int page, int size, String text, String sortBy, String sortDir);
}
