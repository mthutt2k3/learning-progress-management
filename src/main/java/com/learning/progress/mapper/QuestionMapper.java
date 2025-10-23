package com.learning.progress.mapper;

import com.learning.progress.dto.challenge.section.DataItem;
import com.learning.progress.dto.challenge.section.QuestionDto;
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
public interface QuestionMapper {

    List<QuestionDto> toQuestionDtos(List<Question> savedQuestions);

    @Mapping(target = "content", source = "questionContentJson")
    QuestionDto toQuestionDto(Question question);

    Question toQuestionDtos(QuestionDto dto);

    // Object <-> Map<String,Object> for question content
    default Map<String, Object> map(Object content) {
        return JsonUtil.objectToMap(content);
    }

    default Object map(Map<String, Object> map) {
        return JsonUtil.responseToObject(map, Object.class); // hoặc QuestionContent.class nếu có
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

}
