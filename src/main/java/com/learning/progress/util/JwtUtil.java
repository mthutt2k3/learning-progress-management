package com.learning.progress.util;

import com.learning.progress.dto.EmailChangeTokenClaims;
import com.learning.progress.exception.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
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

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secret.getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String generateToken(String username, String role, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("userId", userId);
        return doGenerateToken(claims, username, this.expiration);
    }

    private String doGenerateToken(Map<String, Object> claims, String subject, Long expiration) {
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    public Boolean validateToken(String token, String username) {
        final String tokenUsername = getUsernameFromToken(token);
        return (tokenUsername.equals(username) && !isTokenExpired(token));
    }

    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    public String extractUsernameFromCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new RuntimeException("Cannot access current request context");
        }

        HttpServletRequest request = attributes.getRequest();
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        return getUsernameFromToken(token);
    }
    public boolean isCurrentUser(Long userId) {
        try {
            // Lấy request hiện tại
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return false;
            }

            HttpServletRequest request = attributes.getRequest();
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return false;
            }

            String token = authHeader.substring(7);

            // Lấy userId từ JWT
            Claims claims = getAllClaimsFromToken(token);
            Long currentUserId = ((Number) claims.get("userId")).longValue();

            return currentUserId.equals(userId);
        } catch (Exception e) {
            return false;
        }
    }
    public EmailChangeTokenClaims validateEmailChangeToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            if (isTokenExpired(token)) {
                throw new ApiException("Token has expired", HttpStatus.BAD_REQUEST.value());
            }

            Long userId = ((Number) claims.get("userId")).longValue();
            String newEmail = (String) claims.get("newEmail");

            if (userId == null || newEmail == null) {
                throw new ApiException("Invalid token: missing userId or newEmail", HttpStatus.BAD_REQUEST.value());
            }

            return EmailChangeTokenClaims.builder()
                    .userId(userId).newEmail(newEmail).build();
        } catch (JwtException e) {
            throw new ApiException("Invalid token", HttpStatus.BAD_REQUEST.value());
        }
    }


    public Long extractUserIdFromCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new ApiException("Cannot access current request context", HttpStatus.UNAUTHORIZED.value());
        }

        HttpServletRequest request = attributes.getRequest();
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException("Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED.value());
        }

        String token = authHeader.substring(7);
        Claims claims = getAllClaimsFromToken(token);
        return ((Number) claims.get("userId")).longValue();
    }
}