package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.request.ChangePasswordRequest;
import com.learning.progress.dto.request.LoginRequest;
import com.learning.progress.dto.request.ResetPasswordRequest;
import com.learning.progress.dto.response.LoginResponse;
import com.learning.progress.dto.response.ResetPasswordByTeacherResponse;
import com.learning.progress.entity.RefreshToken;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.AuthMapper;
import com.learning.progress.repository.RefreshTokenRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.AuthService;
import com.learning.progress.service.TokenService;
import com.learning.progress.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

    @Autowired
    private AuthMapper authMapper;

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        if (loginRequest.getUsername() == null || loginRequest.getUsername().trim().isEmpty()) {
            throw new ApiException(Const.USER.USERNAME_EMPTY, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findByUserName(loginRequest.getUsername())
                .orElseThrow(() -> new ApiException(Const.AUTH.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED.value()));

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new ApiException(Const.AUTH.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED.value());
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }

        String roleInput = loginRequest.getLoginRole();
        if (roleInput == null || roleInput.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.MISSING_FIELD, HttpStatus.BAD_REQUEST.value());
        }

        String roleUpper = roleInput.trim().toUpperCase();
        if (!roleUpper.equals("TEACHER") && !roleUpper.equals("STUDENT")) {
            throw new ApiException(Const.VALIDATION.INVALID_FORMAT, HttpStatus.BAD_REQUEST.value());
        }

        RoleName loginRole = RoleName.valueOf(roleUpper);
        RoleName userRole = RoleName.valueOf(user.getRole().getName().toString().toUpperCase());

        switch (loginRole) {
            case TEACHER -> {
                if (!(userRole == RoleName.ADMIN
                        || userRole == RoleName.MANAGER
                        || userRole == RoleName.TEACHER
                        || userRole == RoleName.TEACHING_ASSISTANT)) {
                    throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
                }
            }
            case STUDENT -> {
                if (!(userRole == RoleName.STUDENT || userRole == RoleName.TEST_TAKER)) {
                    throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
                }
            }
            default -> throw new ApiException(Const.VALIDATION.INVALID_FORMAT, HttpStatus.BAD_REQUEST.value());
        }

        String accessToken = jwtUtil.generateToken(user.getUserName(), user.getRole().getName().toString());
        RefreshToken refreshToken = tokenService.createRefreshToken(user);

        return authMapper.toLoginResponse(user, refreshToken, accessToken);
    }

    @Override
    public String resetPasswordByEmail(ResetPasswordRequest request) {
        if (request == null || request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new ApiException(Const.USER.EMAIL_NOT_FOUND, HttpStatus.BAD_REQUEST.value());
        }

        if (!request.getUsername().matches(Const.VALIDATE_INPUT.regexEmail)) {
            throw new ApiException(Const.USER.EMAIL_INVALID, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findByUserName(request.getUsername())
                .orElseThrow(() -> new ApiException(Const.USER.EMAIL_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        String newPassword = generateRandomPassword(8);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        try {
            sendDefaultPassword(user.getEmail(), user, newPassword);
            return user.getEmail();
        } catch (Exception e) {
            throw new ApiException(Const.VALIDATION.OPERATION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private String generateRandomPassword(int length) {
        if (length < 6) {
            throw new ApiException(Const.VALIDATION.INVALID_FORMAT, HttpStatus.BAD_REQUEST.value());
        }
        return RandomStringUtils.secure().nextAlphanumeric(length);
    }

    private void sendDefaultPassword(String toEmail, User user, String defaultPassword) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.INVALID_INPUT, HttpStatus.BAD_REQUEST.value());
        }
        if (defaultPassword == null || defaultPassword.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.INVALID_INPUT, HttpStatus.BAD_REQUEST.value());
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

    @Override
    public void changePassword(ChangePasswordRequest request) {
        String username = jwtUtil.extractUsernameFromCurrentRequest();
        if (username == null || username.trim().isEmpty()) {
            throw new ApiException(Const.USER.USERNAME_EMPTY, HttpStatus.BAD_REQUEST.value());
        }

        if (request.getOldPassword() == null || request.getOldPassword().trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.MISSING_FIELD, HttpStatus.BAD_REQUEST.value());
        }

        if (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.MISSING_FIELD, HttpStatus.BAD_REQUEST.value());
        }

        if (request.getNewPassword().length() < 6) {
            throw new ApiException(Const.VALIDATION.INVALID_FORMAT, HttpStatus.BAD_REQUEST.value());
        }

        if (!request.getNewPassword().matches(Const.VALIDATE_INPUT.regexPass)) {
            throw new ApiException(Const.VALIDATION.INVALID_FORMAT, HttpStatus.BAD_REQUEST.value());
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ApiException(Const.VALIDATION.INVALID_INPUT, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.USER.USERNAME_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new ApiException(Const.AUTH.INVALID_CREDENTIALS, HttpStatus.BAD_REQUEST.value());
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new ApiException(Const.VALIDATION.INVALID_INPUT, HttpStatus.BAD_REQUEST.value());
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    public ResetPasswordByTeacherResponse resetPasswordByTeacher(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new ApiException(Const.USER.USERNAME_EMPTY, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.USER.USERNAME_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        RoleName userRole = RoleName.valueOf(user.getRole().getName().toString().toUpperCase());
        if (!(userRole == RoleName.STUDENT || userRole == RoleName.TEST_TAKER)) {
            throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
        }

        String newPassword = generateRandomPassword(8);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return authMapper.toResetPasswordByTeacherResponse(user, newPassword);
    }

    @Override
    public Map<String, String> refreshAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.MISSING_FIELD, HttpStatus.BAD_REQUEST.value());
        }

        RefreshToken token = refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)
                .orElseThrow(() -> new ApiException(Const.AUTH.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.value()));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(Const.AUTH.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.value());
        }

        User user = token.getUser();
        String newAccessToken = jwtUtil.generateToken(user.getUserName(), user.getRole().getName().toString());

        return Map.of(
                "accessToken", newAccessToken,
                "refreshToken", refreshToken
        );
    }

    @Override
    public void logout(String refreshTokenParam) {
        if (refreshTokenParam == null || refreshTokenParam.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.MISSING_FIELD, HttpStatus.BAD_REQUEST.value());
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new ApiException(Const.VALIDATION.OPERATION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        HttpServletRequest request = attributes.getRequest();
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException(Const.SECURITY.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED.value());
        }

        String accessToken = authHeader.substring(7);

        if (tokenService.isAccessTokenBlacklisted(accessToken)) {
            throw new ApiException(Const.AUTH.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.value());
        }

        Instant expiry = jwtUtil.getExpirationDateFromToken(accessToken).toInstant();
        tokenService.blacklistAccessToken(accessToken, expiry);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(refreshTokenParam)
                .orElseThrow(() -> new ApiException(Const.AUTH.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.value()));
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }
}