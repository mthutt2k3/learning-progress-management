package com.learning.progress.mapper;

import com.learning.progress.dto.AccountDTO;
import com.learning.progress.dto.response.StudentProfileResponse;
import com.learning.progress.dto.request.CreateUserRequest;
import com.learning.progress.dto.request.CreateNewAccountRequest;
import com.learning.progress.dto.response.CreateAccountResponse;
import com.learning.progress.dto.response.CreateUserResponse;
import com.learning.progress.dto.response.UserProfileResponse;
import com.learning.progress.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.OffsetDateTime;
import java.util.Date;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserMapper {

    StudentProfileResponse toStudentProfileResponse(User user);

    User toUser(CreateUserRequest request);

    User toUser(CreateNewAccountRequest request);

    @Mapping(source = "role.name", target = "roleName")
    CreateUserResponse toCreateUserResponse(User user);

    CreateAccountResponse toCreateAccountResponse(User user);

    UserProfileResponse toUserProfileResponse(User user);

    @Mapping(target = "roleName", source = "role.name")
    @Mapping(target = "createAt", source = "createdAt")
    @Mapping(target = "fullName", expression = "java(user.getFirstName() + \" \" + user.getLastName())")
    AccountDTO toAccountDTO(User user);

    default Date map(OffsetDateTime offsetDateTime) {
        return offsetDateTime != null ? Date.from(offsetDateTime.toInstant()) : null;
    }
}