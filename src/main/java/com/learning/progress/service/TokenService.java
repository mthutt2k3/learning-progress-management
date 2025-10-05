package com.learning.progress.service;

import com.learning.progress.entity.RefreshToken;
import com.learning.progress.entity.User;

import java.time.Instant;

public interface TokenService {
    RefreshToken createRefreshToken(User user);
    void blacklistAccessToken(String token, Instant expiresAt);
    boolean isAccessTokenBlacklisted(String token);
}
