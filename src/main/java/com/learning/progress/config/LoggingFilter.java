package com.learning.progress.config;

import com.learning.progress.util.Snowflake;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * LoggingFilter — Ghi log toàn bộ request/response cho mỗi request đến hệ thống.
 * Bao gồm:
 *  - traceId (duy nhất cho từng request, sinh theo Snowflake)
 *  - method, uri, status, duration
 *  - preview body (request + response)
 *  - log exception nếu có lỗi
 */
@Component
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);
    private static final Snowflake snowflake = new Snowflake();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // ✅ Sinh traceId bằng Snowflake ID
        String traceId = snowflake.nextId();
        MDC.put("traceId", traceId);

        // ✅ Bọc lại request/response để đọc body nhiều lần
        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

        // ✅ Log ngay khi request vừa vào
        logInboundRequest(req, traceId);

        long start = System.currentTimeMillis();

        try {
            filterChain.doFilter(req, res);
        } catch (Exception e) {
            // Log lỗi chi tiết nếu có exception
            log.error("[traceId={}] Exception in {} {}: {}", traceId, req.getMethod(), req.getRequestURI(), e.getMessage(), e);
            throw e; // không nuốt exception
        } finally {
            long duration = System.currentTimeMillis() - start;

            // ✅ Log chi tiết request/response sau khi xử lý xong
            logRequestResponse(req, res, traceId, duration);

            // Nếu có lỗi (status 4xx hoặc 5xx) thì log chi tiết body lỗi
            if (res.getStatus() >= 400) {
                String errorBody = getPreview(res.getContentAsByteArray());
                if (!errorBody.isEmpty()) {
                    log.error("[traceId={}] Error Response Body: {}", traceId, errorBody);
                }
            }

            // ✅ Trả lại body response thật cho client
            res.copyBodyToResponse();

            // ✅ Clear traceId để tránh rò rỉ sang thread khác
            MDC.clear();
        }
    }

    /**
     * ✅ Ghi log thông tin request ngay khi vừa nhận
     */
    private void logInboundRequest(ContentCachingRequestWrapper req, String traceId) {
        String method = req.getMethod();
        String uri = req.getRequestURI();
        String query = req.getQueryString() != null ? "?" + req.getQueryString() : "";
        String ip = req.getRemoteAddr();

        log.info("[traceId={}] Incoming request: {} {}{} | from IP={}", traceId, method, uri, query, ip);

        String body = getPreview(req.getContentAsByteArray());
        if (!body.isEmpty()) {
            log.debug("[traceId={}] Request Body: {}", traceId, body);
        }
    }

    /**
     * ✅ Ghi log thông tin request/response sau khi xử lý xong
     */
    private void logRequestResponse(ContentCachingRequestWrapper req,
                                    ContentCachingResponseWrapper res,
                                    String traceId,
                                    long duration) {

        String method = req.getMethod();
        String uri = req.getRequestURI();
        String query = req.getQueryString() != null ? "?" + req.getQueryString() : "";
        int status = res.getStatus();

        log.info("[traceId={}] Completed {} {}{} | status={} | duration={}ms",
                traceId, method, uri, query, status, duration
        );

        // Preview body response
        String responseBody = getPreview(res.getContentAsByteArray());
        if (!responseBody.isEmpty()) {
            log.debug("[traceId={}] Response Body: {}", traceId, responseBody);
        }

        log.trace("[traceId={}] reqSize={}B, resSize={}B",
                traceId,
                req.getContentAsByteArray().length,
                res.getContentAsByteArray().length
        );
    }

    /**
     * ✅ Hàm rút gọn nội dung body cho log (giới hạn 300 ký tự)
     */
    private String getPreview(byte[] content) {
        if (content == null || content.length == 0) return "";
        String text = new String(content, StandardCharsets.UTF_8).trim();
        if (text.length() > 300) text = text.substring(0, 300) + "...";
        return text.replaceAll("\\s+", " ");
    }
}
