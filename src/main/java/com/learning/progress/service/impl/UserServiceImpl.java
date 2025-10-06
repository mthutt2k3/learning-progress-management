package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.dto.response.UserProfileResponse;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.UserService;
import com.learning.progress.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserProfileResponse getCurrentUserProfile() {
        String username = jwtUtil.extractUsernameFromCurrentRequest();

        if (username == null || username.trim().isEmpty()) {
            throw new ApiException(Const.USER.USERNAME_EMPTY, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.AUTH.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        return userMapper.toUserProfileResponse(user);
    }
}