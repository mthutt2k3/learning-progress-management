package com.learning.progress.mapper;

import com.learning.progress.dto.AccountDTO;
import com.learning.progress.dto.StudentProfileDTO;
import com.learning.progress.dto.TeacherProfileDTO;
import com.learning.progress.dto.UserProfileDTO;
import com.learning.progress.dto.request.CreateStudentRequest;
import com.learning.progress.dto.response.StudentProfileResponse;
import com.learning.progress.dto.request.CreateUserRequest;
import com.learning.progress.dto.request.CreateNewAccountRequest;
import com.learning.progress.dto.response.CreateAccountResponse;
import com.learning.progress.dto.response.CreateUserResponse;
import com.learning.progress.dto.response.UserProfileResponse;
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

    StudentProfileResponse toStudentProfileResponse(User user);

    @Mapping(target = "additionalData", expression = "java(request.getParentInfo() != null ? JsonUtil.objectToJson(request.getParentInfo()) : null)")
    User toUser(CreateStudentRequest request);

    User toUser(CreateUserRequest request);

    User toUser(CreateNewAccountRequest request);

    @Mapping(source = "role.name", target = "roleName")
    CreateUserResponse toCreateUserResponse(User user);

    CreateAccountResponse toCreateAccountResponse(User user);

    @Mapping(source = "theme", target = "theme")
    @Mapping(source = "language", target = "language")
    UserProfileResponse toUserProfileResponse(User user);

    @Mapping(target = "roleName", source = "role.name")
    @Mapping(target = "createAt", source = "createdAt")
    @Mapping(target = "fullName", expression = "java(user.getFirstName() + \" \" + user.getLastName())")
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