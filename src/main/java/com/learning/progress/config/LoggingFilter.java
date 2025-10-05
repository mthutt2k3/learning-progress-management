package com.learning.progress.config;

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
import java.util.UUID;

/**
 * LoggingFilter — Ghi log toàn bộ request/response cho mỗi request đến hệ thống.
 * Bao gồm:
 *  - traceId (duy nhất cho từng request)
 *  - method, uri, status, duration
 *  - preview body (request + response)
 *  - log exception nếu có lỗi
 */
@Component
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Gán traceId cho mỗi request để tracking trong toàn bộ flow
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        // Bọc lại request/response để đọc được body nhiều lần
        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(req, res);
        } catch (Exception e) {
            // Log lỗi chi tiết nếu có exception
            log.error("[traceId={}]  Exception in {} {}: {}",
                    traceId, req.getMethod(), req.getRequestURI(), e.getMessage(), e);
            throw e; // không nuốt exception, để Spring xử lý
        } finally {
            long duration = System.currentTimeMillis() - start;

            // Log chi tiết request/response sau khi xử lý xong
            logRequestResponse(req, res, traceId, duration);

            // Nếu có lỗi (status 4xx hoặc 5xx) thì log chi tiết body lỗi
            if (res.getStatus() >= 400) {
                String errorBody = getPreview(res.getContentAsByteArray());
                if (!errorBody.isEmpty()) {
                    log.error("[traceId={}]  Error Response Body: {}", traceId, errorBody);
                }
            }

            // Ghi nội dung response thật ra cho client
            res.copyBodyToResponse();

            // Xóa MDC tránh rò rỉ traceId giữa các thread
            MDC.clear();
        }
    }

    /**
     * Ghi log thông tin request/response cho từng request
     */
    private void logRequestResponse(ContentCachingRequestWrapper req,
                                    ContentCachingResponseWrapper res,
                                    String traceId,
                                    long duration) {

        String method = req.getMethod();
        String uri = req.getRequestURI();
        String query = req.getQueryString() != null ? "?" + req.getQueryString() : "";
        String clientIp = req.getRemoteAddr();
        int status = res.getStatus();

        // Log tổng quan mỗi request
        log.info("[traceId={}] {} {}{} | status={} | ip={} | duration={}ms",
                traceId, method, uri, query, status, clientIp, duration
        );

        // Log preview body request (chỉ khi có dữ liệu)
        String requestBody = getPreview(req.getContentAsByteArray());
        if (!requestBody.isEmpty()) {
            log.debug("[traceId={}] requestBodyPreview={}", traceId, requestBody);
        }

        // Log preview body response (chỉ khi có dữ liệu)
        String responseBody = getPreview(res.getContentAsByteArray());
        if (!responseBody.isEmpty()) {
            log.debug("[traceId={}] responseBodyPreview={}", traceId, responseBody);
        }

        // Log kích thước body
        log.trace("[traceId={}] reqSize={}B, resSize={}B",
                traceId,
                req.getContentAsByteArray().length,
                res.getContentAsByteArray().length
        );
    }

    /**
     * Hàm rút gọn nội dung body cho log (giới hạn 300 ký tự)
     */
    private String getPreview(byte[] content) {
        if (content == null || content.length == 0) return "";
        String text = new String(content, StandardCharsets.UTF_8).trim();
        if (text.length() > 300) text = text.substring(0, 300) + "...";
        return text.replaceAll("\\s+", " "); // bỏ xuống dòng / khoảng trắng thừa
    }
}
