package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.JwtTokenType;
import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.auth.*;
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
import com.learning.progress.util.TraceUtil;
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
import java.util.List;
import java.util.Map;

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
    @Autowired
    private NotificationServiceImpl notificationServiceImpl;

    /**
     * Authenticates a user and generates access and refresh tokens.
     * Validates username, password, user status, and role permissions.
     *
     * @param loginRequest The login request containing username, password, and role.
     * @return LoginResponse containing user details and tokens.
     */
    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        final String method = "login";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} username={} loginRole={}", method, traceId, loginRequest.getUsername(), loginRequest.getLoginRole());

        // Fetch user by username
        User user = userRepository.findByUserNameAndDeletedAtIsNull(loginRequest.getUsername())
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} User not found: {}", method, traceId, loginRequest.getUsername());
                    return new ApiException(Const.USER.NOT_FOUND, HttpStatus.UNAUTHORIZED.value());
                });

        // Validate password
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            log.error("[{}] traceId={} Invalid credentials for username: {}", method, traceId, loginRequest.getUsername());
            throw new ApiException(Const.AUTH.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED.value());
        }

        // Check user status
        if (user.getStatus() == UserStatus.INACTIVE) {
            log.error("[{}] traceId={} User inactive: {}", method, traceId, loginRequest.getUsername());
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }

        // Validate login role
        String roleInput = loginRequest.getLoginRole();
        String roleUpper = roleInput.trim().toUpperCase();
        log.debug("[{}] traceId={} Validating role: {}", method, traceId, roleUpper);

        RoleName loginRole;
        try {
            loginRole = RoleName.valueOf(roleUpper);
        } catch (IllegalArgumentException e) {
            log.error("[{}] traceId={} Invalid login role: {}", method, traceId, roleUpper);
            throw new ApiException(Const.ROLE.INVALID_LOGIN_ROLE, HttpStatus.BAD_REQUEST.value());
        }

        RoleName userRole = RoleName.valueOf(user.getRole().getName().toString().toUpperCase());

        // Check role permissions
        switch (loginRole) {
            case TEACHER -> {
                if (!(userRole == RoleName.ADMIN || userRole == RoleName.MANAGER
                        || userRole == RoleName.TEACHER || userRole == RoleName.TEACHING_ASSISTANT)) {
                    log.error("[{}] traceId={} Forbidden role for teacher login: {}", method, traceId, userRole);
                    throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
                }
            }
            case STUDENT -> {
                if (!(userRole == RoleName.STUDENT || userRole == RoleName.TEST_TAKER)) {
                    log.error("[{}] traceId={} Forbidden role for student login: {}", method, traceId, userRole);
                    throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
                }
            }
            default -> {
                log.error("[{}] traceId={} Invalid login role: {}", method, traceId, loginRole);
                throw new ApiException(Const.ROLE.INVALID_LOGIN_ROLE, HttpStatus.BAD_REQUEST.value());
            }
        }

        // Generate tokens
        RefreshToken refreshToken = tokenService.createRefreshToken(user);
        boolean mustChangePassword = user.isMustChangePassword();

        String accessToken = mustChangePassword ?
                jwtUtil.generateResetPasswordToken(user.getUserName(), user.getRole().getName().toString(), user.getId()) :
                jwtUtil.generateAuthToken(user.getUserName(), user.getRole().getName().toString(), user.getId(), user.getEmail());

        if(user.isRequestResetPasswordByTeacher()){
            user.setRequestResetPasswordByTeacher(false);
        }
        userRepository.save(user);
        log.info("[{}] Login successful for username: {}", method, loginRequest.getUsername());

        LoginResponse response = authMapper.toLoginResponse(user, refreshToken, accessToken, mustChangePassword);
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} username={} durationMs={}", method, traceId, loginRequest.getUsername(), durationMs);
        return response;
    }

    @Override
    public String requestTeacherResetPassword(RequestTeacherResetPassword request) {
        final String method = "requestTeacherResetPassword";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} username={}", method, traceId, request.getUserName());

        // Fetch user by username
        User user = userRepository.findByUserNameAndDeletedAtIsNull(request.getUserName())
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} User not found: {}", method, traceId, request.getUserName());
                    return new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Check user role
        if (user.getRole() == null ||
                !(RoleName.STUDENT.equals(user.getRole().getName())
                        || RoleName.TEST_TAKER.equals(user.getRole().getName()))) {
            throw new ApiException(
                    Const.SECURITY.FORBIDDEN_ROLE_STUDENT_ONLY,
                    HttpStatus.FORBIDDEN.value()
            );
        }

        // Check user status
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }

        user.setRequestResetPasswordByTeacher(true);
        user.setResetPasswordTokenUsed(false);
        userRepository.save(user);

        try {
            // 1. Get all teachers of this student
            List<Long> teacherIds = userRepository.findTeacherIdsByStudentId(user.getId());

            // 2. Get all managers
            List<Long> managerIds = userRepository.findAllManagerIds();

            // 3. Prepare notification content
            String studentName = user.getFullName() != null ? user.getFullName() : user.getUserName();
            String title = "Password Reset Request";
            String message = studentName + " requested password reset.";
            String avatarUrl = user.getAvatarUrl();

            // 4. Send to teachers with teacher URL
            if (!teacherIds.isEmpty()) {
                String teacherUrl = "/teacher/student/" + user.getId() + "/profile";
                notificationServiceImpl.createNotification(
                        teacherIds,
                        user.getId(),  // creatorId = student
                        title,
                        message,
                        teacherUrl,
                        avatarUrl
                );
                log.info("[{}] traceId={} Sent password reset notification to {} teachers", method, traceId, teacherIds.size());
            }

            // 5. Send to managers with manager URL
            if (!managerIds.isEmpty()) {
                String managerUrl = "/manager/student/" + user.getId() + "/profile";
                notificationServiceImpl.createNotification(
                        managerIds,
                        user.getId(),  // creatorId = student
                        title,
                        message,
                        managerUrl,
                        avatarUrl
                );
                log.info("[{}] traceId={} Sent password reset notification to {} managers", method, traceId, managerIds.size());
            }

        } catch (Exception e) {
            log.error("[{}] traceId={} Failed to send password reset notifications: {}", method, traceId, e.getMessage(), e);
            // Don't throw - notification failure shouldn't break the password reset request
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} username={} durationMs={}", method, traceId, request.getUserName(), durationMs);
        return Const.RESULT_MESSAGE_CODE.PASSWORD_RESET_TEACHER_SENT;
    }

    /**
     * Resets a student's password by a teacher, generating a random password.
     *
     * @param username The username of the student.
     * @return ResetPasswordByTeacherResponse with user details and new password.
     */
    @Override
    public ResetPasswordByTeacherResponse resetPasswordByTeacher(String username) {
        final String method = "resetPasswordByTeacher";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} username={}", method, traceId, username);

        // Validate username
        if (username == null || username.trim().isEmpty()) {
            log.error("[{}] traceId={} Username is required", method, traceId);
            throw new ApiException(Const.USERNAME.REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        // Fetch user
        User user = userRepository.findByUserNameAndDeletedAtIsNull(username)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} User not found: {}", method, traceId, username);
                    return new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Check user status
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.error("[{}] traceId={} User inactive: {}", method, traceId, username);
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }
        if(!user.isRequestResetPasswordByTeacher()){
            throw new ApiException(Const.USER.NO_RESET_REQUEST_FROM_STUDENT, HttpStatus.FORBIDDEN.value());
        }

        // Validate role
        RoleName userRole = RoleName.valueOf(user.getRole().getName().toString().toUpperCase());
        if (!(userRole == RoleName.STUDENT || userRole == RoleName.TEST_TAKER)) {
            log.error("[{}] traceId={} Forbidden role for reset: {}", method, traceId, userRole);
            throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE_STUDENT_ONLY, HttpStatus.FORBIDDEN.value());
        }

        // Generate and set new password
        String newPassword = DataUtil.generateRandomPassword(8);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
        log.info("[{}] traceId={} Password reset by teacher for username: {}", method, traceId, username);

        ResetPasswordByTeacherResponse response = authMapper.toResetPasswordByTeacherResponse(user, newPassword);
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} username={} durationMs={}", method, traceId, username, durationMs);
        return response;
    }

    /**
     * Initiates a password reset by sending an email with a reset token.
     *
     * @param request The reset password request containing the username.
     * @return Masked email address of the user.
     */
    @Override
    public String requestResetPasswordByEmail(RequestResetPasswordByEmail request) {
        final String method = "requestResetPasswordByEmail";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} username={}", method, traceId, request.getUserName());

        // Fetch user by username
        User user = userRepository.findByUserNameAndDeletedAtIsNull(request.getUserName())
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} User not found: {}", method, traceId, request.getUserName());
                    return new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Check user status
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.error("[{}] traceId={} User inactive: {}", method, traceId, request.getUserName());
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }

        // Generate and save reset token
        String resetPasswordToken = jwtUtil.generateResetPasswordToken(user.getUserName(), user.getRole().getName().toString(), user.getId());
        log.debug("[{}] traceId={} Reset token generated for user", method, traceId);

        // Send reset email
        try {
            emailService.sendForgotPasswordEmail(user, request, resetPasswordToken);
            log.info("[{}] traceId={} Password reset email sent to: {}", method, traceId, user.getEmail());
            user.setResetPasswordTokenUsed(false);
            userRepository.save(user);

            String result = DataUtil.maskEmail(user.getEmail());
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("[{}] exit traceId={} username={} durationMs={}", method, traceId, request.getUserName(), durationMs);
            return result;
        } catch (Exception e) {
            log.error("[{}] traceId={} Failed to send reset email for username: {}, error: {}", method, traceId, request.getUserName(), e.getMessage());
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
    public String resetPasswordByToken(ResetPasswordByTokenRequest request) {
        final String method = "resetPasswordByToken";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} token={}", method, traceId, request.getToken());

        // ✅ Validate token + user
        Long userId = jwtUtil.validateAndGetUserIdFromResetPasswordToken(request.getToken());

        // Fetch user by reset token
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} Invalid reset token: {}", method, traceId, request.getToken());
                    return new ApiException(Const.USER.NOT_FOUND, HttpStatus.BAD_REQUEST.value());
                });
        if (user.getStatus() == UserStatus.INACTIVE) {
            log.error("[{}] traceId={} User inactive: {}", method, traceId, user.getUserName());
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }
        if (user.getStatus() == UserStatus.PENDING) {
            user.setStatus(UserStatus.ACTIVE);
        }
        if (user.isResetPasswordTokenUsed()) {
            log.error("[{}] traceId={} Have changed password: {}", method, traceId, user.getUserName());
            throw new ApiException(Const.AUTH.HAVE_CHANGED_PASSWORD, HttpStatus.FORBIDDEN.value());
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        user.setResetPasswordTokenUsed(true);
        userRepository.save(user);
        log.info("[{}] traceId={} Password reset successful for user: {}", method, traceId, user.getUserName());

        String result = DataUtil.maskEmail(user.getEmail());
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} user={} durationMs={}", method, traceId, user.getUserName(), durationMs);
        return result;
    }

    /**
     * Changes a user's password after validating the old password and confirming the new one.
     *
     * @param request The change password request containing old and new passwords.
     * @return LoginResponse with new tokens and user details.
     */
    @Override
    public LoginResponse changePassword(ChangePasswordRequest request) {
        final String method = "changePassword";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        String username = jwtUtil.extractUsernameFromCurrentRequest();
        log.info("[{}] enter traceId={} username={}", method, traceId, username);

        // Validate password match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            log.error("[{}] traceId={} New password and confirm password do not match for username: {}", method, traceId, username);
            throw new ApiException(Const.AUTH.PASSWORDS_DO_NOT_MATCH, HttpStatus.BAD_REQUEST.value());
        }

        // Fetch user
        User user = userRepository.findByUserNameAndDeletedAtIsNull(username)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} User not found: {}", method, traceId, username);
                    return new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
        // Check user status
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.error("[{}] traceId={} User inactive: {}", method, traceId, username);
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }
        // Validate old password
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            log.error("[{}] traceId={} Invalid old password for username: {}", method, traceId, username);
            throw new ApiException(Const.AUTH.INVALID_OLD_PASSWORD, HttpStatus.BAD_REQUEST.value());
        }

        // Check if new password is same as old
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            log.error("[{}] traceId={} New password same as old for username: {}", method, traceId, username);
            throw new ApiException(Const.AUTH.NEW_PASSWORD_SAME_AS_OLD, HttpStatus.BAD_REQUEST.value());
        }

        // Update password and logout
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        log.debug("[{}] traceId={} Password updated for username: {}", method, traceId, username);

        logout(request.getRefreshToken());
        log.debug("[{}] traceId={} User logged out after password change: {}", method, traceId, username);

        // Generate new tokens
        String accessToken = jwtUtil.generateAuthToken(user.getUserName(), user.getRole().getName().toString(), user.getId(), user.getEmail());
        RefreshToken refreshToken = tokenService.createRefreshToken(user);
        boolean mustChangePassword = user.isMustChangePassword();

        LoginResponse response = authMapper.toLoginResponse(user, refreshToken, accessToken, mustChangePassword);
        log.info("[{}] Password change successful for username: {}", method, username);
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} username={} durationMs={}", method, traceId, username, durationMs);
        return response;
    }

    /**
     * Refreshes an access token using a valid refresh token.
     *
     * @param refreshToken The refresh token.
     * @return Map containing new access token and the same refresh token.
     */
    @Override
    public Map<String, String> refreshAccessToken(String refreshToken) {
        final String method = "refreshAccessToken";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={}", method, traceId);

        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            log.error("[{}] traceId={} Refresh token required", method, traceId);
            throw new ApiException(Const.VALIDATION.REFRESH_TOKEN_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        RefreshToken token = refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} Invalid refresh token: {}", method, traceId, refreshToken);
                    return new ApiException(Const.AUTH.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED.value());
                });

        if (token.getExpiresAt().isBefore(Instant.now())) {
            log.error("[{}] traceId={} Refresh token expired", method, traceId);
            throw new ApiException(Const.RESULT_MESSAGE_CODE.REFRESH_TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED.value());
        }

        User user = token.getUser();
        String newAccessToken = jwtUtil.generateAuthToken(user.getUserName(), user.getRole().getName().toString(), user.getId(), user.getEmail());
        log.info("[{}] traceId={} Access token refreshed for username: {}", method, traceId, user.getUserName());

        Map<String, String> result = Map.of(
                "accessToken", newAccessToken,
                "refreshToken", refreshToken
        );
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} username={} durationMs={}", method, traceId, user.getUserName(), durationMs);
        return result;
    }


    /**
     * Logs out a user by blacklisting the access token and revoking the refresh token.
     *
     * @param refreshTokenParam The refresh token to revoke.
     */
    @Override
    public void logout(String refreshTokenParam) {
        final String method = "logout";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={}", method, traceId);

        if (refreshTokenParam == null || refreshTokenParam.trim().isEmpty()) {
            log.error("[{}] traceId={} Refresh token required for logout", method, traceId);
            throw new ApiException(Const.TOKEN.REFRESH_TOKEN_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.error("[{}] traceId={} Bearer token required", method, traceId);
            throw new ApiException(Const.SECURITY.AUTH_BEARER_REQUIRED, HttpStatus.UNAUTHORIZED.value());
        }

        String accessToken = authHeader.substring(7);
        if (accessToken.trim().isEmpty()) {
            log.error("[{}] traceId={} Access token required", method, traceId);
            throw new ApiException(Const.SECURITY.ACCESS_TOKEN_REQUIRED, HttpStatus.UNAUTHORIZED.value());
        }

        if (tokenService.isAccessTokenBlacklisted(accessToken)) {
            log.error("[{}] traceId={} Access token blacklisted: {}", method, traceId, accessToken);
            throw new ApiException(Const.AUTH.ACCESS_TOKEN_BLACKLISTED, HttpStatus.UNAUTHORIZED.value());
        }

        Instant expiry = jwtUtil.getExpirationDate(accessToken, JwtTokenType.AUTH).toInstant();
        tokenService.blacklistAccessToken(accessToken, expiry);
        log.debug("[{}] traceId={} Access token blacklisted", method, traceId);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(refreshTokenParam)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} Invalid refresh token: {}", method, traceId, refreshTokenParam);
                    return new ApiException(Const.AUTH.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED.value());
                });
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        log.info("[{}] traceId={} Logout successful, refresh token revoked", method, traceId);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} durationMs={}", method, traceId, durationMs);
    }
}

