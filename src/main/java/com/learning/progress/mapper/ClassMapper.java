package com.learning.progress.mapper;

import com.learning.progress.dto.clazz.ClassDTO;
import com.learning.progress.dto.user.TeacherInfo;
import com.learning.progress.entity.ClassTeacher;
import com.learning.progress.entity.Clazz;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ClassMapper {

    @Mapping(source = "classTeachers", target = "teacherInfos")
    @Mapping(target = "teacherCount", expression = "java(clazzEntity.getClassTeachers() != null ? clazzEntity.getClassTeachers().size() : 0)")
    @Mapping(target = "studentCount", expression = "java(clazzEntity.getClassStudents() != null ? clazzEntity.getClassStudents().size() : 0)")
    ClassDTO toClassDTO(Clazz clazzEntity);

    @Mapping(source = "user.id" , target = "teacherId")
    @Mapping(source = "user.email" , target = "email")
    @Mapping(source = "user.fullName" , target = "fullName")
    @Mapping(source = "user.userName" , target = "userName")
    TeacherInfo toTeacherInfo(ClassTeacher classTeacher);
}