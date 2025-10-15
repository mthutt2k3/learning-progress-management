package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.request.ChangePasswordRequest;
import com.learning.progress.dto.request.ConfirmResetPasswordRequest;
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
import com.learning.progress.service.EmailService;
import com.learning.progress.service.TokenService;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

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

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private EmailService emailService;
    /**
     * Authenticates a user and generates access and refresh tokens.
     * Validates username, password, user status, and role permissions.
     *
     * @param loginRequest The login request containing username, password, and role.
     * @return LoginResponse containing user details and tokens.
     */
    @Override
    public LoginResponse login(LoginRequest loginRequest) {

        User user = userRepository.findByUserName(loginRequest.getUsername())
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED.value()));

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new ApiException(Const.AUTH.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED.value());
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }

        // Validate login role
        String roleInput = loginRequest.getLoginRole();

        String roleUpper = roleInput.trim().toUpperCase();

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
            default -> throw new ApiException(Const.ROLE.INVALID_LOGIN_ROLE, HttpStatus.BAD_REQUEST.value());
        }

        String accessToken = jwtUtil.generateToken(user.getUserName(), user.getRole().getName().toString(), user.getId());
        RefreshToken refreshToken = tokenService.createRefreshToken(user);
        boolean mustChangePassword = user.isMustChangePassword();
        boolean mustUpdateProfile = user.isMustUpdateProfile();

        return authMapper.toLoginResponse(user, refreshToken, accessToken, mustChangePassword, mustUpdateProfile);
    }

    @Override
    public String resetPasswordByEmail(ResetPasswordRequest request) {

        User user = userRepository.findByUserName(request.getUserName())
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Kiểm tra trạng thái hoạt động của người dùng
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }

        // Tạo token đặt lại mật khẩu
        String resetToken = UUID.randomUUID().toString();
        user.setResetPasswordToken(resetToken);
        user.setResetPasswordExpires(OffsetDateTime.now().plusHours(24));
        user.setMustChangePassword(true);
        userRepository.save(user);

        try {
            emailService.sendForgotPasswordEmail(user, request, resetToken);
            return DataUtil.maskEmail(user.getEmail());
        } catch (Exception e) {
            throw new ApiException(Const.AUTH.EMAIL_SEND_FAILED, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    public String confirmResetPassword(ConfirmResetPasswordRequest request) {
        User user = userRepository.findByResetPasswordToken(request.getToken())
                .orElseThrow(() -> new ApiException(Const.AUTH.INVALID_CREDENTIALS, HttpStatus.BAD_REQUEST.value()));

        // Kiểm tra token hết hạn
        if (user.getResetPasswordExpires().isBefore(OffsetDateTime.now())) {
            throw new ApiException(Const.TOKEN.EXPIRED, HttpStatus.BAD_REQUEST.value());
        }

        // Cập nhật mật khẩu mới
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordExpires(null);
        user.setMustChangePassword(false);
        userRepository.save(user);

        return DataUtil.maskEmail(user.getEmail());
    }

    public LoginResponse changePassword(ChangePasswordRequest request) {

        String username = jwtUtil.extractUsernameFromCurrentRequest();

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ApiException(Const.AUTH.PASSWORDS_DO_NOT_MATCH, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new ApiException(Const.AUTH.INVALID_OLD_PASSWORD, HttpStatus.BAD_REQUEST.value());
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new ApiException(Const.AUTH.NEW_PASSWORD_SAME_AS_OLD, HttpStatus.BAD_REQUEST.value());
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        logout(request.getRefreshToken());

        String accessToken = jwtUtil.generateToken(user.getUserName(), user.getRole().getName().toString(), user.getId());
        RefreshToken refreshToken = tokenService.createRefreshToken(user);
        boolean mustChangePassword = user.isMustChangePassword();
        boolean mustUpdateProfile = user.isMustUpdateProfile();

        return authMapper.toLoginResponse(user, refreshToken, accessToken, mustChangePassword, mustUpdateProfile);
    }

    public ResetPasswordByTeacherResponse resetPasswordByTeacher(String username) {
        // Validate username
        if (username == null || username.trim().isEmpty()) {
            throw new ApiException(Const.USERNAME.REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Added: Check user active
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }

        RoleName userRole = RoleName.valueOf(user.getRole().getName().toString().toUpperCase());
        if (!(userRole == RoleName.STUDENT || userRole == RoleName.TEST_TAKER)) {
            throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE_STUDENT_ONLY, HttpStatus.FORBIDDEN.value());
        }

        String newPassword = DataUtil.generateRandomPassword(8);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);

        return authMapper.toResetPasswordByTeacherResponse(user, newPassword);
    }

    public Map<String, String> refreshAccessToken(String refreshToken) {
        // Validate refresh token
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.REFRESH_TOKEN_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        RefreshToken token = refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)
                .orElseThrow(() -> new ApiException(Const.AUTH.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED.value()));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(Const.AUTH.REFRESH_TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED.value());
        }

        User user = token.getUser();
        String newAccessToken = jwtUtil.generateToken(user.getUserName(), user.getRole().getName().toString(), user.getId());

        return Map.of(
                "accessToken", newAccessToken,
                "refreshToken", refreshToken
        );
    }

    public void logout(String refreshTokenParam) {
        // Validate refresh token param
        if (refreshTokenParam == null || refreshTokenParam.trim().isEmpty()) {
            throw new ApiException(Const.TOKEN.REFRESH_TOKEN_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new ApiException(Const.SECURITY.OPERATION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        HttpServletRequest request = attributes.getRequest();
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException(Const.SECURITY.AUTH_BEARER_REQUIRED, HttpStatus.UNAUTHORIZED.value());
        }

        String accessToken = authHeader.substring(7);
        // Added: Check access token after extract
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new ApiException(Const.SECURITY.ACCESS_TOKEN_REQUIRED, HttpStatus.UNAUTHORIZED.value());
        }

        if (tokenService.isAccessTokenBlacklisted(accessToken)) {
            throw new ApiException(Const.AUTH.ACCESS_TOKEN_BLACKLISTED, HttpStatus.UNAUTHORIZED.value());
        }

        Instant expiry = jwtUtil.getExpirationDateFromToken(accessToken).toInstant();
        tokenService.blacklistAccessToken(accessToken, expiry);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(refreshTokenParam)
                .orElseThrow(() -> new ApiException(Const.AUTH.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED.value()));
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }
}