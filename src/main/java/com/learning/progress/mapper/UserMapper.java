package com.learning.progress.mapper;

import com.learning.progress.dto.request.CreateUserRequest;
import com.learning.progress.dto.response.CreateAccountResponse;
import com.learning.progress.dto.response.CreateUserResponse;
import com.learning.progress.dto.response.UserProfileResponse;
import com.learning.progress.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserMapper {

    User toUser(CreateUserRequest request);

    @Mapping(source = "role.name", target = "roleName")
    CreateUserResponse toCreateUserResponse(User user);

    CreateAccountResponse toCreateAccountResponse(User user);

    @Mapping(source = "userName", target = "username")
    @Mapping(source = "role.name", target = "role")
    UserProfileResponse toUserProfileResponse(User user);
}