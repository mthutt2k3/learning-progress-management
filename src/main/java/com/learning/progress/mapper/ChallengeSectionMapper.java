package com.learning.progress.mapper;

import com.learning.progress.common.ResourceType;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.dto.challenge.section.SectionDto;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import com.learning.progress.entity.ChallengeSection;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ChallengeSectionMapper {
    // DTO -> Entity
    ChallengeSection toChallengeSectionEntity(SectionDto sectionDto);

    // Entity -> DTO
    SectionDto toSectionDto(ChallengeSection entity);

    // List<Entity> -> List<DTO>
    List<SectionDto> toSectionDtoList(List<ChallengeSection> entities);

    // Custom mapping Section + Questions -> SectionWithQuestionsDto
    default SectionWithQuestionsDto toSectionWithQuestionsDto(ChallengeSection section, List<QuestionDto> questions) {
        SectionDto sectionDto = toSectionDto(section);
        SectionWithQuestionsDto result = new SectionWithQuestionsDto();
        result.setSection(sectionDto);
        result.setQuestions(questions);
        return result;
    }

    default List<SectionWithQuestionsDto> toSectionWithQuestionsDtoList(List<ChallengeSection> sections,
                                                                        List<List<QuestionDto>> questionsList) {
        if (sections == null || questionsList == null || sections.size() != questionsList.size()) {
            throw new IllegalArgumentException("Section and questionsList size mismatch");
        }
        List<SectionWithQuestionsDto> result = new java.util.ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            result.add(toSectionWithQuestionsDto(sections.get(i), questionsList.get(i)));
        }
        return result;
    }
}
