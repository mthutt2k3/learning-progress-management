package com.learning.progress.service;

import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.LoginRequest;
import com.learning.progress.dto.LoginResponse;
import com.learning.progress.entity.User;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest loginRequest) {
        User user = userRepository.findByUserName(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("User account is not active");
        }

        String token = jwtUtil.generateToken(user.getUserName());
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUsername(user.getUserName());
        response.setRole(user.getRole().getName());
        return response;
    }
}