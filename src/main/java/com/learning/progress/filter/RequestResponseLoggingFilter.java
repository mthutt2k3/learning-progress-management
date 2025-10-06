package com.learning.progress.filter;

import com.learning.progress.util.Snowflake;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@Slf4j
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Snowflake snowflake = new Snowflake(1); // nodeId = 1

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/static") || path.contains("/health") || path.contains("/favicon");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = snowflake.nextId();
        MDC.put("traceId", traceId); // Lưu traceId vào MDC

        try {
            String clientIp = request.getHeader("X-Forwarded-For");
            if (clientIp == null) clientIp = request.getRemoteAddr();

            ContentCachingRequestWrapper reqWrapper = new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper respWrapper = new ContentCachingResponseWrapper(response);

            long startTime = System.currentTimeMillis();
            filterChain.doFilter(reqWrapper, respWrapper);
            long duration = System.currentTimeMillis() - startTime;

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String requestBody = new String(reqWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            String responseBody = new String(respWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);

            requestBody = requestBody.replaceAll("[\\n\\r]", "");
            responseBody = responseBody.replaceAll("[\\n\\r]", "");

            log.info("API_LOG | traceId={} | ip={} | time={} | duration={}ms | method={} | path={} | request={} | response={}",
                    traceId, clientIp, timestamp, duration,
                    request.getMethod(), request.getRequestURI(),
                    requestBody, responseBody
            );

            respWrapper.copyBodyToResponse();
        } finally {
            MDC.clear(); // Xóa MDC sau khi hoàn thành request
        }
    }
}
