package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.JwtTokenType;
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
import org.slf4j.MDC;
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
        String traceId = MDC.get("traceId");
        log.info("[{}] Login attempt for username: {}, role: {}", traceId, loginRequest.getUsername(), loginRequest.getLoginRole());

        // Fetch user by username
        User user = userRepository.findByUserName(loginRequest.getUsername())
                .orElseThrow(() -> {
                    log.error("[{}] User not found: {}", traceId, loginRequest.getUsername());
                    return new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED.value());
                });

        // Validate password
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            log.error("[{}] Invalid credentials for username: {}", traceId, loginRequest.getUsername());
            throw new ApiException(Const.AUTH.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED.value());
        }

        // Check user status
        if (user.getStatus() == UserStatus.INACTIVE) {
            log.error("[{}] User inactive: {}", traceId, loginRequest.getUsername());
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }

        // Validate login role
        String roleInput = loginRequest.getLoginRole();
        String roleUpper = roleInput.trim().toUpperCase();
        log.debug("[{}] Validating role: {}", traceId, roleUpper);

        RoleName loginRole;
        try {
            loginRole = RoleName.valueOf(roleUpper);
        } catch (IllegalArgumentException e) {
            log.error("[{}] Invalid login role: {}", traceId, roleUpper);
            throw new ApiException(Const.ROLE.INVALID_LOGIN_ROLE, HttpStatus.BAD_REQUEST.value());
        }

        RoleName userRole = RoleName.valueOf(user.getRole().getName().toString().toUpperCase());

        // Check role permissions
        switch (loginRole) {
            case TEACHER -> {
                if (!(userRole == RoleName.ADMIN || userRole == RoleName.MANAGER
                        || userRole == RoleName.TEACHER || userRole == RoleName.TEACHING_ASSISTANT)) {
                    log.error("[{}] Forbidden role for teacher login: {}", traceId, userRole);
                    throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
                }
            }
            case STUDENT -> {
                if (!(userRole == RoleName.STUDENT || userRole == RoleName.TEST_TAKER)) {
                    log.error("[{}] Forbidden role for student login: {}", traceId, userRole);
                    throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
                }
            }
            default -> {
                log.error("[{}] Invalid login role: {}", traceId, loginRole);
                throw new ApiException(Const.ROLE.INVALID_LOGIN_ROLE, HttpStatus.BAD_REQUEST.value());
            }
        }

        // Generate tokens
        RefreshToken refreshToken = tokenService.createRefreshToken(user);
        boolean mustChangePassword = user.isMustChangePassword();
        boolean mustUpdateProfile = user.isMustUpdateProfile();

        String accessToken = mustChangePassword ?
                jwtUtil.generateResetPasswordToken(user.getUserName(), user.getRole().getName().toString(), user.getId()) :
                jwtUtil.generateAuthToken(user.getUserName(), user.getRole().getName().toString(), user.getId(), user.getEmail());

        log.info("[{}] Login successful for username: {}", traceId, loginRequest.getUsername());
        return authMapper.toLoginResponse(user, refreshToken, accessToken, mustChangePassword, mustUpdateProfile);
    }

    /**
     * Initiates a password reset by sending an email with a reset token.
     *
     * @param request The reset password request containing the username.
     * @return Masked email address of the user.
     */
    @Override
    public String resetPasswordByEmail(ResetPasswordRequest request) {
        String traceId = MDC.get("traceId");
        log.info("[{}] Password reset requested for username: {}", traceId, request.getUserName());

        // Fetch user by username
        User user = userRepository.findByUserName(request.getUserName())
                .orElseThrow(() -> {
                    log.error("[{}] User not found: {}", traceId, request.getUserName());
                    return new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Check user status
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.error("[{}] User inactive: {}", traceId, request.getUserName());
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }

        // Generate and save reset token
        String resetPasswordToken = jwtUtil.generateResetPasswordToken(user.getUserName(), user.getRole().getName().toString(), user.getId());
        log.debug("[{}] Reset token generated for user is {}", traceId, resetPasswordToken);

        // Send reset email
        try {
            emailService.sendForgotPasswordEmail(user, request, resetPasswordToken);
            log.info("[{}] Password reset email sent to: {}", traceId, user.getEmail());
            return DataUtil.maskEmail(user.getEmail());
        } catch (Exception e) {
            log.error("[{}] Failed to send reset email for username: {}, error: {}",
                    traceId, request.getUserName(), e.getMessage());
            throw new ApiException(Const.AUTH.EMAIL_SEND_FAILED, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /**
     * Confirms a password reset using the provided token and new password.
     *
     * @param request The request containing the reset token and new password.
     * @return Masked email address of the user.
     */
    @Override
    public String confirmResetPassword(ConfirmResetPasswordRequest request) {
        String traceId = MDC.get("traceId");
        log.info("[{}] Confirming password reset with token: {}", traceId, request.getToken());

        // ✅ Validate token + user
        Long userId = jwtUtil.validateAndGetUserIdFromResetPasswordToken(request.getToken());

        // Fetch user by reset token
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("[{}] Invalid reset token: {}", traceId, request.getToken());
                    return new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.BAD_REQUEST.value());
                });
        if (user.getStatus() == UserStatus.INACTIVE) {
            log.error("[{}] User inactive: {}", traceId, user.getUserName());
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }
        if (user.getStatus() == UserStatus.PENDING) {
            user.setStatus(UserStatus.ACTIVE);
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        log.info("[{}] Password reset successful for user: {}", traceId, user.getUserName());

        return DataUtil.maskEmail(user.getEmail());
    }

    /**
     * Changes a user's password after validating the old password and confirming the new one.
     *
     * @param request The change password request containing old and new passwords.
     * @return LoginResponse with new tokens and user details.
     */
    @Override
    public LoginResponse changePassword(ChangePasswordRequest request) {
        String traceId = MDC.get("traceId");
        String username = jwtUtil.extractUsernameFromCurrentRequest();
        log.info("[{}] Password change requested for username: {}", traceId, username);

        // Validate password match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            log.error("[{}] New password and confirm password do not match for username: {}", traceId, username);
            throw new ApiException(Const.AUTH.PASSWORDS_DO_NOT_MATCH, HttpStatus.BAD_REQUEST.value());
        }

        // Fetch user
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> {
                    log.error("[{}] User not found: {}", traceId, username);
                    return new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Validate old password
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            log.error("[{}] Invalid old password for username: {}", traceId, username);
            throw new ApiException(Const.AUTH.INVALID_OLD_PASSWORD, HttpStatus.BAD_REQUEST.value());
        }

        // Check if new password is same as old
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            log.error("[{}] New password same as old for username: {}", traceId, username);
            throw new ApiException(Const.AUTH.NEW_PASSWORD_SAME_AS_OLD, HttpStatus.BAD_REQUEST.value());
        }

        // Update password and logout
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        log.debug("[{}] Password updated for username: {}", traceId, username);

        logout(request.getRefreshToken());
        log.debug("[{}] User logged out after password change: {}", traceId, username);

        // Generate new tokens
        String accessToken = jwtUtil.generateAuthToken(user.getUserName(), user.getRole().getName().toString(), user.getId(), user.getEmail());
        RefreshToken refreshToken = tokenService.createRefreshToken(user);
        boolean mustChangePassword = user.isMustChangePassword();
        boolean mustUpdateProfile = user.isMustUpdateProfile();

        log.info("[{}] Password change successful for username: {}", traceId, username);
        return authMapper.toLoginResponse(user, refreshToken, accessToken, mustChangePassword, mustUpdateProfile);
    }

    /**
     * Resets a student's password by a teacher, generating a random password.
     *
     * @param username The username of the student.
     * @return ResetPasswordByTeacherResponse with user details and new password.
     */
    @Override
    public ResetPasswordByTeacherResponse resetPasswordByTeacher(String username) {
        String traceId = MDC.get("traceId");
        log.info("[{}] Teacher password reset requested for username: {}", traceId, username);

        // Validate username
        if (username == null || username.trim().isEmpty()) {
            log.error("[{}] Username is required", traceId);
            throw new ApiException(Const.USERNAME.REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        // Fetch user
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> {
                    log.error("[{}] User not found: {}", traceId, username);
                    return new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Check user status
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.error("[{}] User inactive: {}", traceId, username);
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }

        // Validate role
        RoleName userRole = RoleName.valueOf(user.getRole().getName().toString().toUpperCase());
        if (!(userRole == RoleName.STUDENT || userRole == RoleName.TEST_TAKER)) {
            log.error("[{}] Forbidden role for reset: {}", traceId, userRole);
            throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE_STUDENT_ONLY, HttpStatus.FORBIDDEN.value());
        }

        // Generate and set new password
        String newPassword = DataUtil.generateRandomPassword(8);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
        log.info("[{}] Password reset by teacher for username: {}", traceId, username);

        return authMapper.toResetPasswordByTeacherResponse(user, newPassword);
    }

    /**
     * Refreshes an access token using a valid refresh token.
     *
     * @param refreshToken The refresh token.
     * @return Map containing new access token and the same refresh token.
     */
    @Override
    public Map<String, String> refreshAccessToken(String refreshToken) {
        String traceId = MDC.get("traceId");
        log.info("[{}] Refresh token request", traceId);

        // Validate refresh token
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            log.error("[{}] Refresh token required", traceId);
            throw new ApiException(Const.VALIDATION.REFRESH_TOKEN_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        // Fetch refresh token
        RefreshToken token = refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)
                .orElseThrow(() -> {
                    log.error("[{}] Invalid refresh token: {}", traceId, refreshToken);
                    return new ApiException(Const.AUTH.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED.value());
                });

        // Check token expiration
        if (token.getExpiresAt().isBefore(Instant.now())) {
            log.error("[{}] Refresh token expired", traceId);
            throw new ApiException(Const.RESULT_MESSAGE_CODE.REFRESH_TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED.value());
        }

        // Generate new access token
        User user = token.getUser();
        String newAccessToken = jwtUtil.generateAuthToken(user.getUserName(), user.getRole().getName().toString(), user.getId(), user.getEmail());
        log.info("[{}] Access token refreshed for username: {}", traceId, user.getUserName());

        return Map.of(
                "accessToken", newAccessToken,
                "refreshToken", refreshToken
        );
    }

    /**
     * Logs out a user by blacklisting the access token and revoking the refresh token.
     *
     * @param refreshTokenParam The refresh token to revoke.
     */
    @Override
    public void logout(String refreshTokenParam) {
        String traceId = MDC.get("traceId");
        log.info("[{}] Logout request", traceId);

        // Validate refresh token
        if (refreshTokenParam == null || refreshTokenParam.trim().isEmpty()) {
            log.error("[{}] Refresh token required for logout", traceId);
            throw new ApiException(Const.TOKEN.REFRESH_TOKEN_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        // Get request attributes
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.error("[{}] Request attributes not found", traceId);
            throw new ApiException(Const.SECURITY.OPERATION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        // Extract access token
        HttpServletRequest request = attributes.getRequest();
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.error("[{}] Bearer token required", traceId);
            throw new ApiException(Const.SECURITY.AUTH_BEARER_REQUIRED, HttpStatus.UNAUTHORIZED.value());
        }

        String accessToken = authHeader.substring(7);
        if (accessToken == null || accessToken.trim().isEmpty()) {
            log.error("[{}] Access token required", traceId);
            throw new ApiException(Const.SECURITY.ACCESS_TOKEN_REQUIRED, HttpStatus.UNAUTHORIZED.value());
        }

        // Check if access token is blacklisted
        if (tokenService.isAccessTokenBlacklisted(accessToken)) {
            log.error("[{}] Access token blacklisted: {}", traceId, accessToken);
            throw new ApiException(Const.AUTH.ACCESS_TOKEN_BLACKLISTED, HttpStatus.UNAUTHORIZED.value());
        }

        // Blacklist access token
        Instant expiry = jwtUtil.getExpirationDate(accessToken, JwtTokenType.AUTH).toInstant();
        tokenService.blacklistAccessToken(accessToken, expiry);
        log.debug("[{}] Access token blacklisted: {}", traceId, accessToken);

        // Revoke refresh token
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(refreshTokenParam)
                .orElseThrow(() -> {
                    log.error("[{}] Invalid refresh token: {}", traceId, refreshTokenParam);
                    return new ApiException(Const.AUTH.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED.value());
                });
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        log.info("[{}] Logout successful, refresh token revoked", traceId);
    }
}