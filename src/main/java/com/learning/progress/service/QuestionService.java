package com.learning.progress.service;


import com.learning.progress.dto.challenge.ChallengeSectionDTO;
import com.learning.progress.dto.challenge.QuestionDTO;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDTO;

import java.util.List;

public interface QuestionService {

    Object createSectionWithQuestions(Long challengeId, SectionWithQuestionsDTO dto);

    ChallengeSectionDTO createSection(Long challengeId, ChallengeSectionDTO dto);

    List<ChallengeSectionDTO> getSectionsByChallengeId(Long challengeId);

    ChallengeSectionDTO updateSection(Long challengeId, Long sectionId, ChallengeSectionDTO dto);

    void deleteSection(Long challengeId, Long sectionId, String deletedBy);

    QuestionDTO createQuestion(Long challengeId, Long sectionId, QuestionDTO dto);

    List<QuestionDTO> getQuestionsByChallengeId(Long challengeId);

    List<QuestionDTO> getQuestionsBySectionId(Long challengeId, Long sectionId);

    QuestionDTO updateQuestion(Long challengeId, Long questionId, QuestionDTO dto);

    void deleteQuestion(Long challengeId, Long questionId, String deletedBy);

}