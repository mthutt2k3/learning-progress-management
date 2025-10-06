package com.learning.progress.service;

import com.learning.progress.dto.request.CreateUserRequest;
import com.learning.progress.dto.response.CreateUserResponse;
import com.learning.progress.dto.response.UserProfileResponse;

public interface UserService {
    UserProfileResponse getCurrentUserProfile();

    CreateUserResponse createUser(CreateUserRequest request);
}
