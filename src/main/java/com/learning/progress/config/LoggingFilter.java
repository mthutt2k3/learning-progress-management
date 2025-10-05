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
import java.util.Collections;
import java.util.Enumeration;
import java.util.stream.Collectors;

@Component
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);
    private static final Snowflake snowflake = new Snowflake();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = snowflake.nextId();
        MDC.put("traceId", traceId);

        // Bọc request/response để đọc body nhiều lần
        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();

        try {
            // ✅ Cho request đi qua controller
            filterChain.doFilter(req, res);
        } catch (Exception e) {
            log.error("[traceId={}] Exception in {} {}: {}", traceId, req.getMethod(), req.getRequestURI(), e.getMessage(), e);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - start;

            logFullRequestResponse(req, res, traceId, duration);

            // ✅ Ghi body thật trả lại client
            res.copyBodyToResponse();
            MDC.clear();
        }
    }

    /**
     * ✅ Log chi tiết request + response sau khi xử lý
     */
    private void logFullRequestResponse(ContentCachingRequestWrapper req,
                                        ContentCachingResponseWrapper res,
                                        String traceId,
                                        long duration) {

        String method = req.getMethod();
        String uri = req.getRequestURI();
        String query = req.getQueryString() != null ? "?" + req.getQueryString() : "";
        String ip = req.getRemoteAddr();
        int status = res.getStatus();

        // 🧠 Log request meta
        log.info("[traceId={}] {} {}{} | status={} | duration={}ms | ip={}",
                traceId, method, uri, query, status, duration, ip);

        // 🧠 Log headers
        String headers = getAllHeaders(req);
        log.debug("[traceId={}] Request Headers: {}", traceId, headers);

        // 🧠 Log request body
        String requestBody = getFullBody(req.getContentAsByteArray());
        if (!requestBody.isEmpty()) {
            log.debug("[traceId={}] Request Body: {}", traceId, requestBody);
        }

        // 🧠 Log response body
        String responseBody = getFullBody(res.getContentAsByteArray());
        if (!responseBody.isEmpty()) {
            if (status >= 400) {
                log.error("[traceId={}] Error Response Body: {}", traceId, responseBody);
            } else {
                log.debug("[traceId={}] Response Body: {}", traceId, responseBody);
            }
        }

        // 🧠 Log size
        log.trace("[traceId={}] reqSize={}B, resSize={}B",
                traceId,
                req.getContentAsByteArray().length,
                res.getContentAsByteArray().length);
    }

    private String getAllHeaders(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) return "{}";

        return Collections.list(headerNames).stream()
                .map(name -> name + ": " + request.getHeader(name))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private String getFullBody(byte[] content) {
        if (content == null || content.length == 0) return "";
        return new String(content, StandardCharsets.UTF_8).trim();
    }
}
