package com.learning.progress.mapper;

import com.learning.progress.dto.clazz.student.ClassStudentResponse;
import com.learning.progress.entity.ClassStudent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClassStudentMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.userName", target = "userName")
    @Mapping(source = "user.fullName", target = "fullName")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "clazz.id", target = "classId")
    @Mapping(source = "clazz.className", target = "className")
    @Mapping(source = "clazz.syllabus.id", target = "syllabusId")
    @Mapping(source = "clazz.status", target = "classStatus")
    ClassStudentResponse toClassStudentResponse(ClassStudent classStudent);
}