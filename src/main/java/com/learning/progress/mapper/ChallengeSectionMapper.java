package com.learning.progress.mapper;

import com.learning.progress.common.QuestionType;
import com.learning.progress.dto.challenge.section.*;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.entity.Question;
import com.learning.progress.util.JsonUtil;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ChallengeSectionMapper {

    ChallengeSectionMapper INSTANCE = Mappers.getMapper(ChallengeSectionMapper.class);

    /* =========================================================
       =============== ENTITY ↔ DTO MAPPING =====================
       ========================================================= */

    @Mapping(target = "challenge", source = "challenge")
    @Mapping(target = "id", source = "sectionDto.id")
    ChallengeSection toChallengeSectionEntity(SectionDto sectionDto, DailyChallenge challenge);

    SectionDto toSectionDto(ChallengeSection entity);

    List<SectionDto> toSectionDtoList(List<ChallengeSection> entities);

    @Mapping(target = "content", source = "questionContentJson")
    QuestionDto toQuestionDto(Question question);

    Question toQuestionEntity(QuestionDto dto);

    List<QuestionDto> toQuestionDtos(List<Question> questions);

    /* =========================================================
       =============== COMPOSITE MAPPING ========================
       ========================================================= */

    @Mapping(source = "section", target = "section")
    @Mapping(source = "questions", target = "questions")
    SectionWithQuestionsDto toSectionWithQuestionsDto(ChallengeSection section, List<QuestionDto> questions);

    @Mapping(source = "section", target = "section")
    @Mapping(source = "section.questions", target = "questions")
    SectionWithQuestionsDto toSectionWithQuestionsDto(ChallengeSection section);

    default List<SectionWithQuestionsDto> toSectionWithQuestionsDtoList(
            List<ChallengeSection> sections,
            List<List<QuestionDto>> questionsList
    ) {
        if (sections == null || questionsList == null || sections.size() != questionsList.size()) {
            throw new IllegalArgumentException("Sections and questionsList size mismatch");
        }

        return IntStream.range(0, sections.size())
                .mapToObj(i -> toSectionWithQuestionsDto(sections.get(i), questionsList.get(i)))
                .toList();
    }

    /* =========================================================
       =============== STUDENT DTO MAPPING ======================
       ========================================================= */

    @Mapping(source = "section", target = "section")
    @Mapping(source = "questions", target = "questions", qualifiedByName = "toStudentQuestionDtoList")
    StudentSectionWithQuestionsDto toStudentSectionWithQuestionsDto(SectionWithQuestionsDto sectionWithQuestionsDto);

    // QuestionDto → StudentQuestionDto
    @Mapping(source = "id", target = "id")
    @Mapping(source = "questionText", target = "questionText")
    @Mapping(source = "orderNumber", target = "orderNumber")
    @Mapping(source = "questionType", target = "questionType")
    @Mapping(target = "content", expression = "java(mapContentByQuestionType(questionDto.getContent(), questionDto.getQuestionType()))")
    StudentSectionWithQuestionsDto.StudentQuestionDto toStudentQuestionDto(QuestionDto questionDto);

    // Custom mapping for content based on QuestionType
    default StudentDataContent mapContentByQuestionType(DataContent content, String questionType) {
        if (content == null || content.getData() == null) {
            return new StudentDataContent();
        }

        // Parse QuestionType
        QuestionType type;
        try {
            type = QuestionType.valueOf(questionType);
        } catch (IllegalArgumentException e) {
            return new StudentDataContent();
        }

        // Handle REWRITE: no content
        if (type == QuestionType.REWRITE) {
            return null;
        }

        // Map DataItems based on QuestionType
        List<StudentDataItem> studentDataItems = content.getData().stream()
                .map(item -> mapDataItemByQuestionType(item, type))
                .toList();

        StudentDataContent studentDataContent = new StudentDataContent();
        studentDataContent.setData(studentDataItems);
        return studentDataContent;
    }

    default StudentDataItem mapDataItemByQuestionType(DataItem item, QuestionType questionType) {
        StudentDataItem studentItem = new StudentDataItem();
        studentItem.setId(item.getId());
        studentItem.setValue(item.getValue());
        return studentItem;
    }

    /* =========================================================
       =============== JSON ↔ OBJECT HELPERS ====================
       ========================================================= */

    default Map<String, Object> map(Object content) {
        return JsonUtil.objectToMap(content);
    }

    default Object map(Map<String, Object> map) {
        return JsonUtil.responseToObject(map, Object.class);
    }

    default List<DataItem> mapToDataItemList(Object value) {
        if (value == null) return Collections.emptyList();
        return JsonUtil.responseToListObject(value, DataItem.class);
    }

    default Object mapFromDataItemList(List<DataItem> list) {
        return list == null ? null : JsonUtil.responseToObject(list, Object.class);
    }

    @Mapping(target = "data", source = "fullContent.data", qualifiedByName = "mapToStudentDataItems")
    StudentDataContent toStudentDataContent(DataContent fullContent, @Context QuestionType questionType);

    // Custom mapping logic
    @Named("mapToStudentDataItems")
    default List<StudentDataItem> mapToStudentDataItems(List<DataItem> dataItems,@Context QuestionType questionType) {
        if (dataItems == null) {
            return Collections.emptyList();
        }

        return dataItems.stream()
                .map(item -> {
                    String value = (questionType == QuestionType.REWRITE) ? null : item.getValue();
                    return StudentDataItem.builder()
                            .id(item.getId())
                            .value(value)
                            .build();
                })
                .toList();
    }

    // Cập nhật method này để dùng toStudentDataContent có questionType
    @Named("toStudentQuestionDtoList")
    default List<StudentSectionWithQuestionsDto.StudentQuestionDto> toStudentQuestionDtoList(List<QuestionDto> questionDtos) {
        if (questionDtos == null) {
            return Collections.emptyList();
        }
        return questionDtos.stream()
                .map(dto -> {
                    StudentSectionWithQuestionsDto.StudentQuestionDto studentDto = new StudentSectionWithQuestionsDto.StudentQuestionDto();
                    studentDto.setId(dto.getId());
                    studentDto.setQuestionText(dto.getQuestionText());
                    studentDto.setOrderNumber(dto.getOrderNumber());
                    studentDto.setQuestionType(dto.getQuestionType());

                    QuestionType type = QuestionType.valueOf(dto.getQuestionType());
                    StudentDataContent content = toStudentDataContent(dto.getContent(), type);
                    studentDto.setContent(content);

                    return studentDto;
                })
                .toList();
    }
}