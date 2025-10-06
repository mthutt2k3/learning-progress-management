package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.request.CreateAccountRequest;
import com.learning.progress.dto.request.CreateUserRequest;
import com.learning.progress.dto.response.CreateUserResponse;
import com.learning.progress.dto.response.UserProfileResponse;
import com.learning.progress.entity.Role;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.UserMapper;
import com.learning.progress.repository.RoleRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.AccountService;
import com.learning.progress.service.UserService;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EntityManager entityManager;

    @Override
    public UserProfileResponse getCurrentUserProfile() {
        // Validate username from JWT
        String username = jwtUtil.extractUsernameFromCurrentRequest();
        if (username == null || username.trim().isEmpty()) {
            throw new ApiException(Const.AUTH.INVALID_TOKEN_USERNAME, HttpStatus.UNAUTHORIZED.value());
        }

        // Find user
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.AUTH.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Validate user status
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }

        // Map to response
        return userMapper.toUserProfileResponse(user);
    }

    @Override
    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request) {
        if (request.getGender() != null && !DataUtil.isValidGender(request.getGender())) {
            throw new ApiException("Invalid gender format", 400);
        }
        if (request.getPhoneNumber() != null && !DataUtil.isValidPhoneNumber(request.getPhoneNumber())) {
            throw new ApiException("Invalid phone number format", 400);
        }

        // Tìm role
        Role role = roleRepository.findByName(request.getRoleName())
                .orElseThrow(() -> new ApiException("Invalid role: " + request.getRoleName(), 400));

        // Ánh xạ từ DTO sang entity
        User user = userMapper.toUser(request);
        user.setRole(role);

        // Lưu user và flush
        userRepository.saveAndFlush(user);
        entityManager.clear();

        // Lưu user (các trường audit được tự động gán bởi BaseEntity)
        userRepository.save(user);

        //Auto generate username and password
        user.setUserName(DataUtil.generateUsername(user.getRole().getName().toString(), user.getId()));
        user.setPassword(DataUtil.generateRandomPassword(8));
        // Auto-generate account
        accountService.createAccountForUser(CreateAccountRequest
                .builder()
                        .userId(user.getId())
                        .userName(user.getUserName())
                        .password(user.getPassword())
                        .build()
                );
        // Ánh xạ sang CreateUserResponse
        return userMapper.toCreateUserResponse(user);
    }
}