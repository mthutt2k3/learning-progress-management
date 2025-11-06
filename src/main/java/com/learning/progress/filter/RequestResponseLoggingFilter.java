package com.learning.progress.filter;

import com.learning.progress.common.Const;
import com.learning.progress.util.Snowflake;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
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

@Component
@Slf4j
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Snowflake snowflake = new Snowflake(1);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/static")
                || path.contains("/health")
                || path.contains("/favicon")
                || path.contains("/swagger-ui")
                || path.contains("/v3/api-docs")
                || path.contains("/learning-progress-management/api/v1/notifications/sse/stream");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = snowflake.nextId();
        MDC.put(Const.LOGGING.TRACE_ID, traceId);
        request.setAttribute(Const.LOGGING.TRACE_ID, traceId);

        try {
            String clientIp = getClientIp(request);

            // Wrap request/response để cache body
            ContentCachingRequestWrapper reqWrapper = new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper respWrapper = new ContentCachingResponseWrapper(response);

            long startTime = System.currentTimeMillis();
            filterChain.doFilter(reqWrapper, respWrapper);
            long duration = System.currentTimeMillis() - startTime;

            // CHỈ LOG NẾU KHÔNG PHẢI SSE
            if (!isSseRequest(request, response)) {
                logApiCall(traceId, clientIp, reqWrapper, respWrapper, duration, request);
                respWrapper.copyBodyToResponse(); // An toàn: không phải SSE
            } else {
                logSseConnection(traceId, clientIp, request, duration);
                // KHÔNG GỌI copyBodyToResponse() → giữ stream mở
            }

        } finally {
            MDC.clear();
        }
    }

    // --- HELPER METHODS ---

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private boolean isSseRequest(HttpServletRequest request, HttpServletResponse response) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        String contentType = response.getContentType();
        return uri.contains("/notifications/sse/")
                && "text/event-stream".equalsIgnoreCase(accept)
                && contentType != null && contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private void logApiCall(String traceId, String clientIp,
                            ContentCachingRequestWrapper reqWrapper,
                            ContentCachingResponseWrapper respWrapper,
                            long duration, HttpServletRequest request) {

        String requestBody = getBodySafely(reqWrapper);
        String responseBody = getBodySafely(respWrapper);

        log.info("[{}] API_LOG | ip={} | time={} | duration={}ms | method={} | path={} | request={} | response={}",
                traceId, clientIp, getTimestamp(), duration,
                request.getMethod(), request.getRequestURI(),
                requestBody, responseBody
        );
    }

    private void logSseConnection(String traceId, String clientIp, HttpServletRequest request, long duration) {
        log.info("[{}] SSE_CONNECT | ip={} | time={} | duration={}ms | method={} | path={} | userId={}",
                traceId, clientIp, getTimestamp(), duration,
                request.getMethod(), request.getRequestURI(),
                request.getParameter("userId")
        );
    }

    private String getBodySafely(ContentCachingRequestWrapper wrapper) {
        try {
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length > 0) {
                String body = new String(buf, 0, Math.min(buf.length, 5000), StandardCharsets.UTF_8);
                return body.replaceAll("[\\n\\r]", "").trim();
            }
        } catch (Exception e) {
            log.warn("Failed to read request body", e);
        }
        return "<binary or empty>";
    }

    private String getBodySafely(ContentCachingResponseWrapper wrapper) {
        try {
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length > 0) {
                String body = new String(buf, 0, Math.min(buf.length, 5000), StandardCharsets.UTF_8);
                return body.replaceAll("[\\n\\r]", "").trim();
            }
        } catch (Exception e) {
            log.warn("Failed to read response body", e);
        }
        return "<binary or empty>";
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}