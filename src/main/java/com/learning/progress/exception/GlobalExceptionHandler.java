package com.learning.progress.exception;

import com.fasterxml.jackson.core.JsonParseException;
import com.learning.progress.common.RoleName;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.util.TraceUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

@Hidden
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<DataResponse<Object>> handleApiException(ApiException ex, WebRequest request) {
        DataResponse<Object> response = DataResponse.builder()
                .traceId(TraceUtil.getTraceId())
                .success(false)
                .error(ex.getMessage())
                .status(ex.getStatus())
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(response, HttpStatus.valueOf(ex.getStatus()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<DataResponse<Object>> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        DataResponse<Object> response = DataResponse.builder()
                .traceId(TraceUtil.getTraceId())
                .success(false)
                .error("Internal server error: " + ex.getMessage())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<DataResponse<Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex, WebRequest request) {
        log.error("Invalid input: {} | traceId={}", ex.getMessage(), TraceUtil.getTraceId());
        String errorMessage = String.format("Invalid value '%s' for parameter '%s'. Expected one of: %s",
                ex.getValue(), ex.getName(), Arrays.toString(RoleName.values()));
        DataResponse<Object> response = DataResponse.builder()
                .traceId(TraceUtil.getTraceId())
                .success(false)
                .error(errorMessage)
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    // Xử lý lỗi xác thực (401 Unauthorized)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<DataResponse<Object>> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        DataResponse<Object> response = DataResponse.builder()
                .traceId(TraceUtil.getTraceId())
                .success(false)
                .error("Authentication failed: " + ex.getMessage())
                .status(HttpStatus.UNAUTHORIZED.value())
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<DataResponse<Object>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, WebRequest request) {

        // Lấy message của lỗi đầu tiên
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Validation error");

        DataResponse<Object> response = DataResponse.builder()
                .traceId(TraceUtil.getTraceId())
                .success(false)
                .error(errorMessage)  // ✅ Không cần kèm prefix
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }


    // Xử lý lỗi quyền truy cập (403 Forbidden)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<DataResponse<Object>> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        DataResponse<Object> response = DataResponse.builder()
                .traceId(TraceUtil.getTraceId())
                .success(false)
                .error("Access denied: " + ex.getMessage())
                .status(HttpStatus.FORBIDDEN.value())
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<DataResponse<Object>> handleJsonParseError(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        String message = "Invalid JSON format";
        Throwable cause = ex.getCause();

        // Lỗi JSON syntax cơ bản
        if (cause instanceof JsonParseException jsonParseEx) {
            String originalMsg = jsonParseEx.getOriginalMessage();

            if (originalMsg.contains("Unexpected character")
                    || originalMsg.contains("was expecting double-quote")) {
                message = "JSON syntax error: Object keys must be enclosed in double quotes";
            } else if (originalMsg.contains("Unexpected end-of-input")) {
                message = "JSON is incomplete or malformed";
            } else {
                message = "Invalid JSON: " + originalMsg;
            }
        }

        // 🔥 Lỗi Enum không hợp lệ
        else if (cause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException invalidEx) {
            Class<?> targetType = invalidEx.getTargetType();

            // Nếu target type là Enum
            if (targetType.isEnum()) {
                Object[] allowed = targetType.getEnumConstants();
                String allowedValues = Arrays.stream(allowed)
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));

                message = "Invalid value for " + targetType.getSimpleName()
                        + ". Allowed values are: " + allowedValues;
            }
        }

        DataResponse<Object> response = DataResponse.builder()
                .traceId(TraceUtil.getTraceId())
                .success(false)
                .error(message)
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

}