package com.learning.progress.service.impl;

import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.request.ChangePasswordRequest;
import com.learning.progress.dto.request.LoginRequest;
import com.learning.progress.dto.response.LoginResponse;
import com.learning.progress.dto.response.ResetPasswordByTeacherResponse;
import com.learning.progress.entity.RefreshToken;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.RefreshTokenRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.AuthService;
import com.learning.progress.service.TokenService;
import com.learning.progress.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    public LoginResponse loginStudent(LoginRequest loginRequest) {
        User user = userRepository.findByUserName(loginRequest.getUsername())
                .orElseThrow(() -> new ApiException("Invalid username or password", 401));

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("User account is not active");
        }

        String accessToken = jwtUtil.generateToken(user.getUserName(), user.getRole().getName());

        RefreshToken refreshToken = tokenService.createRefreshToken(user);

        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken.getToken());
        response.setUsername(user.getUserName());
        response.setRole(user.getRole().getName());

        return response;
    }


    public LoginResponse loginTeacher(LoginRequest loginRequest) {
        User user = userRepository.findByUserName(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("User account is not active");
        }

        String accessToken = jwtUtil.generateToken(user.getUserName(), user.getRole().getName());

        RefreshToken refreshToken = tokenService.createRefreshToken(user);

        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken.getToken());
        response.setUsername(user.getUserName());
        response.setRole(user.getRole().getName());

        return response;
    }

    public void resetPasswordByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new RuntimeException("Email must not be empty");
        }

        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new RuntimeException("Invalid email format");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email does not exist in the system"));

        String newPassword = generateRandomPassword(8);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        try {
            sendDefaultPassword(email, user, newPassword);
        } catch (Exception e) {
            throw new RuntimeException("Unable to send email. Please try again later");
        }
    }

    private String generateRandomPassword(int length) {
        if (length < 6) {
            throw new RuntimeException("Password length must be at least 6 characters");
        }

        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }

        return sb.toString();
    }

    private void sendDefaultPassword(String toEmail, User user, String defaultPassword) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            throw new RuntimeException("Recipient email is invalid");
        }
        if (defaultPassword == null || defaultPassword.trim().isEmpty()) {
            throw new RuntimeException("Default password cannot be empty");
        }

        String fullName = (user.getFirstName() != null ? user.getFirstName() : "")
                + " "
                + (user.getLastName() != null ? user.getLastName() : "");
        String username = user.getUserName() != null ? user.getUserName() : "(chưa có)";

        String subject = "🔐 Cấp lại mật khẩu tài khoản học tập";
        StringBuilder content = new StringBuilder();

        content.append("Xin chào ").append(fullName.trim().isEmpty() ? "bạn" : fullName).append(",\n\n")
                .append("Hệ thống đã cấp lại mật khẩu mới cho tài khoản của bạn.\n\n")
                .append("👤 Thông tin đăng nhập:\n")
                .append("• Tên đăng nhập: ").append(username).append("\n")
                .append("• Mật khẩu mới: ").append(defaultPassword).append("\n\n")
                .append("📢 Lưu ý: Vì lý do bảo mật, bạn vui lòng đăng nhập và đổi mật khẩu ngay sau khi truy cập hệ thống.\n\n")
                .append("Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng liên hệ với đội ngũ hỗ trợ.\n\n")
                .append("Trân trọng,\n")
                .append("Đội ngũ Hỗ trợ Hệ thống Học tập Camkey 🎓");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(content.toString());

        mailSender.send(message);
    }

    public void changePassword(ChangePasswordRequest request) {

        String username = jwtUtil.extractUsernameFromCurrentRequest();
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("Username must not be empty");
        }

        if (request.getOldPassword() == null || request.getOldPassword().trim().isEmpty()) {
            throw new RuntimeException("Old password must not be empty");
        }

        if (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            throw new RuntimeException("New password must not be empty");
        }

        if (request.getNewPassword().length() < 6) {
            throw new RuntimeException("New password must be at least 6 characters long");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New password and confirm password do not match");
        }

        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("Old password is incorrect");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new RuntimeException("New password cannot be the same as old password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public ResetPasswordByTeacherResponse resetPasswordByTeacher(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("Username must not be empty");
        }

        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("Username does not exist in the system"));

        String newPassword = generateRandomPassword(8);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        ResetPasswordByTeacherResponse response = new ResetPasswordByTeacherResponse();
        response.setUsername(username);
        response.setNewPassword(newPassword);
        return response;
    }

    public Map<String, String> refreshAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new RuntimeException("Refresh token must not be empty");
        }

        RefreshToken token = refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)
                .orElseThrow(() -> new RuntimeException("Invalid or revoked refresh token"));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("Refresh token expired");
        }

        User user = token.getUser();
        String newAccessToken = jwtUtil.generateToken(user.getUserName(), user.getRole().getName());

        return Map.of(
                "accessToken", newAccessToken,
                "refreshToken", refreshToken
        );
    }

    public void logout(String refreshTokenParam) {
        if (refreshTokenParam == null || refreshTokenParam.trim().isEmpty()) {
            throw new RuntimeException("Refresh token must not be empty");
        }

        // Lấy access token từ request header
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new RuntimeException("Cannot access current request context");
        }

        HttpServletRequest request = attributes.getRequest();
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }

        String accessToken = authHeader.substring(7);

        // Check nếu access token đã bị blacklist
        if (tokenService.isAccessTokenBlacklisted(accessToken)) {
            throw new RuntimeException("Access token is already blacklisted");
        }

        // Blacklist access token
        Instant expiry = jwtUtil.getExpirationDateFromToken(accessToken).toInstant();
        tokenService.blacklistAccessToken(accessToken, expiry);

        // Revoke refresh token
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(refreshTokenParam)
                .orElseThrow(() -> new RuntimeException("Invalid or already revoked refresh token"));
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }
}