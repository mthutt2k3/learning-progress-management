package com.learning.progress.mapper;

import com.learning.progress.util.JsonUtil;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {JsonUtil.class}
)
public class SectionMapper {
}
