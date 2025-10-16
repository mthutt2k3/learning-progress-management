package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.dto.request.ResetPasswordRequest;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    public EmailServiceImpl(JavaMailSender mailSender, SpringTemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Override
    @Async("taskExecutor")
    public void sendNewAccountEmail(User user, String username, String password) {
        try {
            String fullName = (user.getFirstName() != null ? user.getFirstName() : "")
                    + " "
                    + (user.getLastName() != null ? user.getLastName() : "");

            Map<String, Object> templateVariables = new HashMap<>();
            templateVariables.put("fullName", fullName.trim().isEmpty() ? "bạn" : fullName.trim());
            templateVariables.put("username", username);
            templateVariables.put("password", password);

            String subject = "🎉 Tài khoản học tập của bạn đã được tạo";
            String templatePath = "email/create-account-email";

            this.sendEmail(user.getEmail(), subject, templatePath, templateVariables);
        } catch (Exception e) {
            throw new ApiException(Const.VALIDATION.EMAIL_SEND_FAILED, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    @Override
    @Async("taskExecutor")
    public void sendChangeEmailConfirmation(User user, String newEmail, String token, String domain, String path) {
        try {
            String fullName = (user.getFirstName() != null ? user.getFirstName() : "")
                    + " "
                    + (user.getLastName() != null ? user.getLastName() : "");
            Map<String, Object> templateVariables = new HashMap<>();
            templateVariables.put("fullName", fullName.trim().isEmpty() ? "bạn" : fullName.trim());
            templateVariables.put("newEmail", newEmail);
            String confirmLink = domain + (path.startsWith("/") ? path : "/" + path) + "?token=" + token;
            templateVariables.put("confirmLink", confirmLink);

            String subject = "🔄 Xác nhận thay đổi email tài khoản học tập";
            String templatePath = "email/confirm-change-email";

            this.sendEmail(newEmail, subject, templatePath, templateVariables);
        } catch (Exception e) {
            throw new ApiException(Const.VALIDATION.EMAIL_SEND_FAILED, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }


    @Override
    @Async("taskExecutor")
    public void sendForgotPasswordEmail(User user, ResetPasswordRequest request, String resetToken) {
        try {
            String fullName = (user.getFirstName() != null ? user.getFirstName() : "")
                    + " "
                    + (user.getLastName() != null ? user.getLastName() : "");
            String username = user.getUserName() != null ? user.getUserName() : "(chưa có)";
            Map<String, Object> templateVariables = new HashMap<>();
            templateVariables.put("fullName", fullName.trim().isEmpty() ? "bạn" : fullName.trim());
            templateVariables.put("username", username);
            // Tạo link reset password từ domain và path
            String resetLink = request.getDomain() + (request.getPath().startsWith("/") ? request.getPath() : "/" + request.getPath()) + "?token=" + resetToken;
            templateVariables.put("resetLink", resetLink);

            String subject = "🔐 Yêu cầu đặt lại mật khẩu tài khoản học tập";
            String templatePath = "email/reset-password-email";

            this.sendEmail(user.getEmail(), subject, templatePath, templateVariables);
        } catch (Exception e) {
            throw new ApiException(Const.AUTH.EMAIL_SEND_FAILED, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    public void sendEmail(String toEmail, String subject, String templatePath, Map<String, Object> templateVariables) throws MessagingException {
        // Validate inputs
        if (toEmail == null || toEmail.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.EMAIL_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }
        if (!Pattern.matches(Const.VALIDATE_INPUT.regexEmail, toEmail)) {
            throw new ApiException(Const.USER.EMAIL_INVALID, HttpStatus.BAD_REQUEST.value());
        }
        if (subject == null || subject.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.SUBJECT_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }
        if (templatePath == null || templatePath.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.TEMPLATE_PATH_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }
        if (templateVariables == null) {
            throw new ApiException(Const.VALIDATION.TEMPLATE_VARIABLES_NULL, HttpStatus.BAD_REQUEST.value());
        }

        // Create Thymeleaf context
        Context context = new Context();
        templateVariables.forEach(context::setVariable);

        // Render email content from Thymeleaf template
        String emailContent = templateEngine.process(templatePath, context);

        // Send email
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(emailContent, true); // true: supports HTML

        mailSender.send(message);
    }

}