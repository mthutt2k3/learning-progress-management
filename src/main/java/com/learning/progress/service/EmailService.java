package com.learning.progress.service;

import com.learning.progress.entity.User;
import jakarta.mail.MessagingException;

import java.util.Map;

public interface EmailService {
    void sendForgotPasswordEmail(String toEmail, String subject, String templatePath, Map<String, Object> templateVariables) throws MessagingException;
    void sendCreateAccountEmail(String toEmail, String subject, String templatePath, Map<String, Object> templateVariables) throws MessagingException;
//    void sendEmail(String toEmail, String subject, String templatePath, Map<String, Object> templateVariables) throws MessagingException;
    void sendNewAccountEmail(User user, String username, String password);

}
