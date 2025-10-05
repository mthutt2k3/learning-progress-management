package com.learning.progress.service.impl;

import com.learning.progress.entity.BlacklistedToken;
import com.learning.progress.entity.RefreshToken;
import com.learning.progress.entity.User;
import com.learning.progress.repository.BlacklistedTokenRepository;
import com.learning.progress.repository.RefreshTokenRepository;
import com.learning.progress.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class TokenServiceImpl implements TokenService {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    public String generateRefreshToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public RefreshToken createRefreshToken(User user) {
        String token = generateRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(token);
        refreshToken.setIssuedAt(Instant.now());
        refreshToken.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        refreshToken.setRevoked(false);
        return refreshTokenRepository.save(refreshToken);
    }

    public boolean isAccessTokenBlacklisted(String token) {
        return blacklistedTokenRepository.existsByToken(token);
    }

    public void blacklistAccessToken(String token, Instant expiresAt) {
        BlacklistedToken blacklistedToken = new BlacklistedToken();
        blacklistedToken.setToken(token);
        blacklistedToken.setBlacklistedAt(Instant.now());
        blacklistedToken.setExpiresAt(expiresAt);
        blacklistedTokenRepository.save(blacklistedToken);
    }
}
