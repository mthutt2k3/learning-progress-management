package com.learning.progress.mapper;

import com.learning.progress.dto.response.LoginResponse;
import com.learning.progress.dto.response.ResetPasswordByTeacherResponse;
import com.learning.progress.entity.RefreshToken;
import com.learning.progress.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthMapper {

    @Mapping(source = "user.userName", target = "username")
    @Mapping(source = "user.role.name", target = "role")
    @Mapping(source = "refreshToken.token", target = "refreshToken")
    @Mapping(source = "accessToken", target = "accessToken")
    @Mapping(source = "mustChangePassword", target = "mustChangePassword")
    LoginResponse toLoginResponse(User user, RefreshToken refreshToken, String accessToken, boolean mustChangePassword);

    @Mapping(source = "user.userName", target = "username")
    @Mapping(source = "newPassword", target = "newPassword")
    ResetPasswordByTeacherResponse toResetPasswordByTeacherResponse(User user, String newPassword);
}
