package com.learning.progress.service;

import com.learning.progress.dto.challenge.section.ChallengeSectionDto;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;

public interface ChallengeSectionService {
    SectionWithQuestionsDto createSection(Long challengeId, SectionWithQuestionsDto dto);
    SectionWithQuestionsDto getSection(Long id);
    SectionWithQuestionsDto updateSection(Long id, SectionWithQuestionsDto dto);
    void deleteSection(Long id);
}
