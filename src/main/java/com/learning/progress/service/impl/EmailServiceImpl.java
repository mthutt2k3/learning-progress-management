package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.exception.ApiException;
import com.learning.progress.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

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
    public void sendForgotPasswordEmail(String toEmail, String subject, String templatePath, Map<String, Object> templateVariables) throws MessagingException {
        sendEmail(toEmail, subject, templatePath, templateVariables);
    }

    @Override
    public void sendCreateAccountEmail(String toEmail, String subject, String templatePath, Map<String, Object> templateVariables) throws MessagingException {
        sendEmail(toEmail, subject, templatePath, templateVariables);
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