package com.learning.progress.service;

import com.learning.progress.dto.request.ResetPasswordRequest;
import com.learning.progress.entity.User;
import jakarta.mail.MessagingException;

import java.util.Map;

public interface EmailService {
    void sendForgotPasswordEmail(User user, ResetPasswordRequest request, String resetToken);
    void sendNewAccountEmail(User user, String username, String password);

}
