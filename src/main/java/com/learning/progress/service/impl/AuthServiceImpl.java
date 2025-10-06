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
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

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

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        // Validate username
        if (loginRequest == null) {
            throw new ApiException(Const.VALIDATION.REQUEST_NULL, HttpStatus.BAD_REQUEST.value());
        }
        if (loginRequest.getUsername() == null || loginRequest.getUsername().trim().isEmpty()) {
            throw new ApiException(Const.USER.USERNAME_EMPTY, HttpStatus.BAD_REQUEST.value());
        }
        // Added: Validate password
        if (loginRequest.getPassword() == null || loginRequest.getPassword().trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.PASSWORD_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findByUserName(loginRequest.getUsername())
                .orElseThrow(() -> new ApiException(Const.AUTH.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED.value()));

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new ApiException(Const.AUTH.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED.value());
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }

        // Validate login role
        String roleInput = loginRequest.getLoginRole();
        if (roleInput == null || roleInput.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.LOGIN_ROLE_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        String roleUpper = roleInput.trim().toUpperCase();
        if (!roleUpper.equals("TEACHER") && !roleUpper.equals("STUDENT")) {
            throw new ApiException(Const.VALIDATION.INVALID_LOGIN_ROLE, HttpStatus.BAD_REQUEST.value());
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
            default -> throw new ApiException(Const.VALIDATION.INVALID_LOGIN_ROLE, HttpStatus.BAD_REQUEST.value());
        }

        String accessToken = jwtUtil.generateToken(user.getUserName(), user.getRole().getName().toString());
        RefreshToken refreshToken = tokenService.createRefreshToken(user);

        return authMapper.toLoginResponse(user, refreshToken, accessToken);
    }

    @Override
    public String resetPasswordByEmail(String userName) {
        // Validate request
        if (userName == null || userName.trim().isEmpty()) {
            throw new ApiException(Const.USER.USERNAME_EMPTY, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findByUserName(userName)
                .orElseThrow(() -> new ApiException(Const.USER.USERNAME_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Added: Check user active status for reset
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }

        // Added: Validate email format of user
        if (user.getEmail() == null || user.getEmail().trim().isEmpty() || !Pattern.matches(Const.VALIDATE_INPUT.regexEmail, user.getEmail())) {
            throw new ApiException(Const.USER.EMAIL_INVALID, HttpStatus.BAD_REQUEST.value());
        }

        String newPassword = DataUtil.generateRandomPassword(8);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        try {
            sendDefaultPassword(user.getEmail(), user, newPassword);
            return user.getEmail();
        } catch (Exception e) {
            throw new ApiException(Const.VALIDATION.EMAIL_SEND_FAILED, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private void sendDefaultPassword(String toEmail, User user, String defaultPassword) throws MessagingException {
        // Validate inputs with details
        if (toEmail == null || toEmail.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.EMAIL_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }
        // Added: Email format check
        if (!Pattern.matches(Const.VALIDATE_INPUT.regexEmail, toEmail)) {
            throw new ApiException(Const.USER.EMAIL_INVALID, HttpStatus.BAD_REQUEST.value());
        }
        if (defaultPassword == null || defaultPassword.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.PASSWORD_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        String fullName = (user.getFirstName() != null ? user.getFirstName() : "")
                + " "
                + (user.getLastName() != null ? user.getLastName() : "");
        String username = user.getUserName() != null ? user.getUserName() : "(chưa có)";
        String subject = "🔐 Cấp lại mật khẩu tài khoản học tập";

        // Tạo context cho Thymeleaf
        Context context = new Context();
        context.setVariable("fullName", fullName.trim().isEmpty() ? "bạn" : fullName);
        context.setVariable("username", username);
        context.setVariable("defaultPassword", defaultPassword);

        // Render nội dung email từ template
        String emailContent = templateEngine.process("email/reset-password-email", context);

        // Gửi email
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(emailContent, true); // true: hỗ trợ HTML

        mailSender.send(message);
    }

    public void changePassword(ChangePasswordRequest request) {
        // Validate request
        if (request == null) {
            throw new ApiException(Const.VALIDATION.REQUEST_NULL, HttpStatus.BAD_REQUEST.value());
        }

        String username = jwtUtil.extractUsernameFromCurrentRequest();
        if (username == null || username.trim().isEmpty()) {
            throw new ApiException(Const.USER.USERNAME_EMPTY, HttpStatus.BAD_REQUEST.value());
        }

        // Validate old password
        if (request.getOldPassword() == null || request.getOldPassword().trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.OLD_PASSWORD_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        // Validate new password
        if (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.NEW_PASSWORD_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        if (request.getNewPassword().length() < 6) {
            throw new ApiException(Const.ERROR_MESSAGE.PASSWORD_TOO_SHORT, HttpStatus.BAD_REQUEST.value());
        }

        // Added: Check confirm password null/empty
        if (request.getConfirmPassword() == null || request.getConfirmPassword().trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.CONFIRM_PASSWORD_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        if (!request.getNewPassword().matches(Const.VALIDATE_INPUT.regexPass)) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_PASSWORD_FORMAT, HttpStatus.BAD_REQUEST.value());
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ApiException(Const.VALIDATION.PASSWORDS_DO_NOT_MATCH, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.USER.USERNAME_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new ApiException(Const.AUTH.INVALID_OLD_PASSWORD, HttpStatus.BAD_REQUEST.value());
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new ApiException(Const.VALIDATION.NEW_PASSWORD_SAME_AS_OLD, HttpStatus.BAD_REQUEST.value());
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public ResetPasswordByTeacherResponse resetPasswordByTeacher(String username) {
        // Validate username
        if (username == null || username.trim().isEmpty()) {
            throw new ApiException(Const.USER.USERNAME_EMPTY, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.USER.USERNAME_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

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
        String newAccessToken = jwtUtil.generateToken(user.getUserName(), user.getRole().getName().toString());

        return Map.of(
                "accessToken", newAccessToken,
                "refreshToken", refreshToken
        );
    }

    public void logout(String refreshTokenParam) {
        // Validate refresh token param
        if (refreshTokenParam == null || refreshTokenParam.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.REFRESH_TOKEN_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new ApiException(Const.VALIDATION.OPERATION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR.value());
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