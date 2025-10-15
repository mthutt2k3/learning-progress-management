package com.learning.progress.mapper;

import com.learning.progress.dto.clazz.AddTeacherToClassRequest;
import com.learning.progress.dto.clazz.ClassTeacherResponse;
import com.learning.progress.entity.ClassTeacher;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClassTeacherMapper {

    @Mapping(target = "clazz", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "joinedAt", ignore = true)
    @Mapping(target = "leftAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ClassTeacher toEntity(AddTeacherToClassRequest request);

    @Mapping(source = "clazz.id", target = "classId")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.userName", target = "userName")
    @Mapping(source = "user.firstName", target = "firstName")
    @Mapping(source = "user.lastName", target = "lastName")
    @Mapping(source = "user.email", target = "email")
    ClassTeacherResponse toClassTeacherResponse(ClassTeacher classTeacher);
}
