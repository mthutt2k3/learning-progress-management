package com.learning.progress.mapper;

import com.learning.progress.dto.clazz.history.ClassHistoryDTO;
import com.learning.progress.entity.ClassHistory;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JsonUtil;
import org.mapstruct.*;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {DataUtil.class}
)
public interface ClassHistoryMapper {
    @Mapping(source = "clazz.id", target = "classId")
    @Mapping(source = "clazz.className", target = "className")
    @Mapping(source = "actionBy.id", target = "actionById")
    @Mapping(source = "actionBy.userName", target = "actionByUsername")
    @Mapping(expression = "java(history.getActionBy() != null ? DataUtil.getEmailPrefix(history.getActionBy().getEmail()) : null)", target = "actionByEmailPrefix")
    @Mapping(expression = "java(history.getActionBy() != null ? history.getActionBy().getFullName() : null)",
            target = "actionByFullName")
    ClassHistoryDTO toClassHistoryDTO(ClassHistory history);
}
