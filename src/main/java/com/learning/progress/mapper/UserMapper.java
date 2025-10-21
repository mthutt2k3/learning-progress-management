package com.learning.progress.mapper;

import com.learning.progress.dto.account.AccountDTO;
import com.learning.progress.dto.user.*;
import com.learning.progress.dto.account.CreateNewAccountRequest;
import com.learning.progress.entity.ClassTeacher;
import com.learning.progress.entity.User;
import com.learning.progress.util.JsonUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.OffsetDateTime;
import java.util.Date;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {JsonUtil.class}
)
public interface UserMapper {

    @Mapping(target = "additionalData", expression = "java(request.getParentInfo() != null ? JsonUtil.objectToJson(request.getParentInfo()) : null)")
    User toUser(CreateStudentRequest request);

    User toUser(CreateUserRequest request);

    User toUser(CreateNewAccountRequest request);

    User toUser(UpdateUserRequest request);

    @Mapping(target = "roleName", source = "role.name")
    @Mapping(target = "createAt", source = "createdAt")
    AccountDTO toAccountDTO(User user);

    default Date map(OffsetDateTime offsetDateTime) {
        return offsetDateTime != null ? Date.from(offsetDateTime.toInstant()) : null;
    }

    @Mapping(target = "roleName", source = "role.name")
    UserProfileDTO toUserProfileDTO(User user);

    @Mapping(target = "roleName", source = "role.name")
    StudentProfileDTO toStudentProfileDTO(User user);

    @Mapping(target = "roleName", source = "role.name")
    TeacherProfileDTO toTeacherProfileDTO(User user);

}