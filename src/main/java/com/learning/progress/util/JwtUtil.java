package com.learning.progress.util;

import com.learning.progress.common.JwtTokenType;
import com.learning.progress.dto.user.EmailChangeTokenClaims;
import com.learning.progress.exception.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    // ---------------- JWT configuration ----------------
    @Value("${jwt.secret}")
    private String authSecret;

    @Value("${jwt.expiration}")
    private Long authExpiration;

    @Value("${jwt.email-change.secret}")
    private String emailChangeSecret;

    @Value("${jwt.email-change.expiration}")
    private Long emailChangeExpiration;

    @Value("${jwt.reset-pw.secret:}") // ví dụ nếu có thêm reset
    private String resetPasswordSecret;

    @Value("${jwt.reset-pw.expiration:0}")
    private Long resetPasswordExpiration;

    private final Map<JwtTokenType, String> secretMap = new HashMap<>();
    private final Map<JwtTokenType, Long> expirationMap = new HashMap<>();

    @PostConstruct
    private void initMaps() {
        secretMap.put(JwtTokenType.AUTH, authSecret);
        secretMap.put(JwtTokenType.EMAIL_CHANGE, emailChangeSecret);
        secretMap.put(JwtTokenType.RESET_PASSWORD, resetPasswordSecret);

        expirationMap.put(JwtTokenType.AUTH, authExpiration);
        expirationMap.put(JwtTokenType.EMAIL_CHANGE, emailChangeExpiration);
        expirationMap.put(JwtTokenType.RESET_PASSWORD, resetPasswordExpiration);
    }

    // ---------------- Generic JWT Methods ----------------

    private Key keyFromType(JwtTokenType type) {
        String secret = secretMap.get(type);
        if (secret == null) throw new ApiException("Secret for token type " + type + " is not configured", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    private Claims getAllClaims(String token, JwtTokenType type) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(keyFromType(type))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            throw new ApiException("Invalid token", HttpStatus.BAD_REQUEST.value());
        }
    }

    private <T> T getClaim(String token, JwtTokenType type, Function<Claims, T> resolver) {
        return resolver.apply(getAllClaims(token, type));
    }

    public boolean isTokenExpired(String token, JwtTokenType type) {
        Date exp = getClaim(token, type, Claims::getExpiration);
        return exp.before(new Date());
    }

    public String generateToken(Map<String, Object> claims, String subject, JwtTokenType type) {
        Long expMs = expirationMap.get(type);
        if (expMs == null) throw new ApiException("Expiration for token type " + type + " is not configured", HttpStatus.INTERNAL_SERVER_ERROR.value());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expMs))
                .signWith(keyFromType(type), SignatureAlgorithm.HS512)
                .compact();
    }

    // ---------------- Normal JWT Methods ----------------
    public Date getExpirationDate(String token, JwtTokenType type) {
        return getClaim(token, type, Claims::getExpiration);
    }

    public String generateAuthToken(String username, String role, Long userId, String email) {
        return generateToken(Map.of("role", role, "userId", userId,"email", email), username, JwtTokenType.AUTH);
    }
    public String generateChangeEmailToken(String username, String newEmail, Long userId) {
        return generateToken(Map.of("newEmail", newEmail, "userId", userId), username, JwtTokenType.EMAIL_CHANGE);
    }

    public String getUsernameFromAuthToken(String token) {
        return getClaim(token, JwtTokenType.AUTH, Claims::getSubject);
    }

    public Long getUserIdFromAuthToken(String token) {
        return getClaim(token, JwtTokenType.AUTH, claims -> ((Number) claims.get("userId")).longValue());
    }

    public boolean validateAuthToken(String token, String username) {
        return username.equals(getUsernameFromAuthToken(token)) && !isTokenExpired(token, JwtTokenType.AUTH);
    }

    // ---------------- Reset Password JWT Methods ----------------

    public String generateResetPasswordToken(String username, String roleName, Long userId) {
        return generateToken(
                Map.of("userId", userId, "roleName", roleName),
                username,
                JwtTokenType.RESET_PASSWORD
        );
    }

    public String getUsernameFromResetPasswordToken(String token) {
        return getClaim(token, JwtTokenType.RESET_PASSWORD, Claims::getSubject);
    }

    public Long getUserIdFromResetPasswordToken(String token) {
        return getClaim(token, JwtTokenType.RESET_PASSWORD,
                claims -> ((Number) claims.get("userId")).longValue()
        );
    }

    public boolean validateResetPasswordToken(String token, String username) {
        return username.equals(getUsernameFromResetPasswordToken(token))
                && !isTokenExpired(token, JwtTokenType.RESET_PASSWORD);
    }

    public Long validateAndGetUserIdFromResetPasswordToken(String token) {
        if (isTokenExpired(token, JwtTokenType.RESET_PASSWORD)) {
            throw new ApiException("Reset password token has expired", HttpStatus.BAD_REQUEST.value());
        }

        Claims claims = getAllClaims(token, JwtTokenType.RESET_PASSWORD);
        Long userId = ((Number) claims.get("userId")).longValue();

        if (userId == null) {
            throw new ApiException("Invalid token: missing userId", HttpStatus.BAD_REQUEST.value());
        }

        return userId;
    }

    // ---------------- Email Change JWT Methods ----------------

    public String generateEmailChangeToken(String username, String newEmail, Long userId) {
        return generateToken(Map.of("newEmail", newEmail, "userId", userId), username, JwtTokenType.EMAIL_CHANGE);
    }

    public EmailChangeTokenClaims validateEmailChangeToken(String token) {
        if (isTokenExpired(token, JwtTokenType.EMAIL_CHANGE)) {
            throw new ApiException("Email change token has expired", HttpStatus.BAD_REQUEST.value());
        }

        Claims claims = getAllClaims(token, JwtTokenType.EMAIL_CHANGE);
        Long userId = ((Number) claims.get("userId")).longValue();
        String newEmail = (String) claims.get("newEmail");

        if (userId == null || newEmail == null) {
            throw new ApiException("Invalid token: missing userId or newEmail", HttpStatus.BAD_REQUEST.value());
        }

        return EmailChangeTokenClaims.builder()
                .userId(userId)
                .newEmail(newEmail)
                .build();
    }

    // ---------------- Request Utilities ----------------

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) throw new ApiException("Cannot access current request context", HttpStatus.UNAUTHORIZED.value());
        return attrs.getRequest();
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException("Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED.value());
        }
        return authHeader.substring(7);
    }

    public String extractUsernameFromCurrentRequest() {
        return getUsernameFromAuthToken(extractTokenFromRequest(getCurrentRequest()));
    }

    public Long extractUserIdFromCurrentRequest() {
        return getUserIdFromAuthToken(extractTokenFromRequest(getCurrentRequest()));
    }
    public String extractEmailFromCurrentRequest() {
        return getEmailFromAuthToken(extractTokenFromRequest(getCurrentRequest()));
    }

    public boolean isCurrentUser(Long userId) {
        try {
            return userId.equals(extractUserIdFromCurrentRequest());
        } catch (Exception e) {
            return false;
        }
    }
    public String getEmailFromAuthToken(String token) {
        return getClaim(token, JwtTokenType.AUTH, claims -> (String) claims.get("email"));
    }
}
