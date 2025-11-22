package com.learning.progress.service.impl;

import com.learning.progress.entity.BlacklistedToken;
import com.learning.progress.entity.RefreshToken;
import com.learning.progress.entity.User;
import com.learning.progress.repository.BlacklistedTokenRepository;
import com.learning.progress.repository.RefreshTokenRepository;
import com.learning.progress.service.TokenService;
import com.learning.progress.common.Const;
import com.learning.progress.util.TraceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@Slf4j
public class TokenServiceImpl implements TokenService {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    public String generateRefreshToken() {
        final String method = "generateRefreshToken";
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] {} enter", traceId, method);

        try {
            SecureRandom random = new SecureRandom();
            byte[] bytes = new byte[64];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            // Log masked token (first/last 6 chars) for troubleshooting without exposing full token
            String masked = token.length() > 12 ? token.substring(0,6) + "..." + token.substring(token.length()-6) : "*****";
            log.debug("[{}] {} generated token masked={}", traceId, method, masked);
            log.debug("[{}] {} exit", traceId, method);
            return token;
        } catch (Exception ex) {
            log.error("[{}] {} error generating refresh token: {}", traceId, method, ex.getMessage(), ex);
            throw ex;
        }
    }

    public RefreshToken createRefreshToken(User user) {
        final String method = "createRefreshToken";
        String traceId = TraceUtil.getTraceId();
        long startNs = System.nanoTime();
        log.info("[{}] {} enter userId={}", traceId, method, user != null ? user.getId() : null);

        try {
            String token = generateRefreshToken();
            RefreshToken refreshToken = new RefreshToken();
            refreshToken.setUser(user);
            refreshToken.setToken(token);
            refreshToken.setIssuedAt(Instant.now());
            refreshToken.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
            refreshToken.setRevoked(false);

            RefreshToken saved = refreshTokenRepository.save(refreshToken);

            // log masked token generation and saved metadata
            String masked = token.length() > 12 ? token.substring(0,6) + "..." + token.substring(token.length()-6) : "*****";
            log.info("[{}] {} {}", traceId, method, String.format(Const.TOKEN.REFRESH_TOKEN_GENERATED, user.getId()));
            log.debug("[{}] {} token masked={} refreshId={} expiresAt={}", traceId, method, masked, saved.getId(), saved.getExpiresAt());

            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("[{}] {} exit userId={} durationMs={}", traceId, method, user.getId(), durationMs);
            return saved;
        } catch (Exception ex) {
            log.error("[{}] {} {}", traceId, method, String.format(Const.TOKEN.REFRESH_TOKEN_CREATE_FAILED, user != null ? user.getId() : null, ex.getMessage()));
            throw ex;
        }
    }

    public boolean isAccessTokenBlacklisted(String token) {
        final String method = "isAccessTokenBlacklisted";
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] {} enter tokenMask={}", traceId, method, token != null && token.length() > 12 ? token.substring(0,6) + "..." + token.substring(token.length()-6) : "*****");

        try {
            boolean exists = blacklistedTokenRepository.existsByToken(token);
            log.debug("[{}] {} exists={}", traceId, method, exists);
            return exists;
        } catch (Exception ex) {
            log.error("[{}] {} error checking blacklist: {}", traceId, method, ex.getMessage(), ex);
            throw ex;
        }
    }

    public void blacklistAccessToken(String token, Instant expiresAt) {
        final String method = "blacklistAccessToken";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] {} enter expiresAt={}", traceId, method, expiresAt);

        try {
            if (isAccessTokenBlacklisted(token)) {
                log.warn("[{}] {} {}", traceId, method, Const.TOKEN.ACCESS_TOKEN_ALREADY_BLACKLISTED);
                return;
            }
            BlacklistedToken blacklistedToken = new BlacklistedToken();
            blacklistedToken.setToken(token);
            blacklistedToken.setBlacklistedAt(Instant.now());
            blacklistedToken.setExpiresAt(expiresAt);
            blacklistedTokenRepository.save(blacklistedToken);

            log.info("[{}] {} {}", traceId, method, String.format(Const.TOKEN.ACCESS_TOKEN_BLACKLISTED, expiresAt));
        } catch (Exception ex) {
            log.error("[{}] {} failed to blacklist token: {}", traceId, method, ex.getMessage(), ex);
            throw ex;
        } finally {
            log.debug("[{}] {} exit", traceId, method);
        }
    }
}
