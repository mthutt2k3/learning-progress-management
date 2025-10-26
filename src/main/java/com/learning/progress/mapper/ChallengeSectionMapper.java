package com.learning.progress.mapper;

import com.learning.progress.dto.challenge.section.DataItem;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.dto.challenge.section.SectionDto;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.entity.Question;
import com.learning.progress.util.JsonUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ChallengeSectionMapper {
    // DTO -> Entity

    @Mapping(target = "challenge", source = "challenge")
    @Mapping(target = "id", source = "sectionDto.id")
    ChallengeSection toChallengeSectionEntity(SectionDto sectionDto, DailyChallenge challenge);

    // Entity -> DTO
    SectionDto toSectionDto(ChallengeSection entity);

    // List<Entity> -> List<DTO>
    List<SectionDto> toSectionDtoList(List<ChallengeSection> entities);

    List<QuestionDto> toQuestionDtos(List<Question> savedQuestions);

    @Mapping(target = "content", source = "questionContentJson")
    QuestionDto toQuestionDto(Question question);

    Question toQuestionDtos(QuestionDto dto);

    @Mapping(source = "section",target = "section")
    @Mapping(source = "questions",target = "questions")
    SectionWithQuestionsDto toSectionWithQuestionsDto(ChallengeSection section, List<QuestionDto> questions);

    @Mapping(target = "section", source = "section")
    @Mapping(target = "questions", source = "section.questions")
    SectionWithQuestionsDto toSectionWithQuestionsDto(ChallengeSection section);

    // Object <-> Map<String,Object> for question content
    default Map<String, Object> map(Object content) {
        return JsonUtil.objectToMap(content);
    }

    default Object map(Map<String, Object> map) {
        return JsonUtil.responseToObject(map, Object.class); // hoặc DataContent.class nếu có
    }
    // Chuyển Object (thường là List hoặc Map) sang List<DataItem>
    default List<DataItem> mapToDataItemList(Object value) {
        if (value == null) return Collections.emptyList();
        return JsonUtil.responseToListObject(value, DataItem.class);
    }

    // Ngược lại: List<DataItem> -> Object (Map hoặc JSON)
    default Object mapFromDataItemList(List<DataItem> list) {
        return JsonUtil.responseToObject(list, Object.class);
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
