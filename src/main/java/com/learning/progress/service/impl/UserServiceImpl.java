package com.learning.progress.service.impl;

import com.learning.progress.dto.response.UserProfileResponse;
import com.learning.progress.entity.User;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.UserService;
import com.learning.progress.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    public UserProfileResponse getCurrentUserProfile() {
        String username = jwtUtil.extractUsernameFromCurrentRequest();

        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfileResponse userProfileResponse = new UserProfileResponse();

        userProfileResponse.setUsername(user.getUserName());
        userProfileResponse.setFirstName(user.getFirstName());
        userProfileResponse.setLastName(user.getLastName());
        userProfileResponse.setEmail(user.getEmail());
        userProfileResponse.setPhoneNumber(user.getPhoneNumber());
        userProfileResponse.setDateOfBirth(user.getDateOfBirth());
        userProfileResponse.setGender(user.getGender());
        userProfileResponse.setStatus(user.getStatus());
        userProfileResponse.setAvatarUrl(user.getAvatarUrl());
        userProfileResponse.setRole(user.getRole().getName());

        return userProfileResponse;
    }
}
