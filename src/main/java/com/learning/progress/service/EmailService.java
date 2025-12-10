package com.learning.progress.service;

import com.learning.progress.dto.auth.RequestResetPasswordByEmail;
import com.learning.progress.entity.User;

public interface EmailService {
    void sendForgotPasswordEmail(User user, RequestResetPasswordByEmail request, String resetToken);
    void sendNewAccountEmail(User user, String username, String password);
    void sendChangeEmailConfirmation(User user, String newEmail, String token, String domain, String path);
}
