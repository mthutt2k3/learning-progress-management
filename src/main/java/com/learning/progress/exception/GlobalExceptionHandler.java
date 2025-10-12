package com.learning.progress.exception;

import com.learning.progress.common.RoleName;
import com.learning.progress.dto.response.DataResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

@Hidden
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<DataResponse<Object>> handleApiException(ApiException ex, WebRequest request) {
        DataResponse<Object> response = DataResponse.builder()
                .traceId(MDC.get("traceId"))
                .success(false)
                .error(ex.getMessage())
                .status(ex.getStatus())
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(response, HttpStatus.valueOf(ex.getStatus()));
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<DataResponse<Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex, WebRequest request) {
        log.error("Invalid input: {} | traceId={}", ex.getMessage(), MDC.get("traceId"));
        String errorMessage = String.format("Invalid value '%s' for parameter '%s'. Expected one of: %s",
                ex.getValue(), ex.getName(), Arrays.toString(RoleName.values()));
        DataResponse<Object> response = DataResponse.builder()
                .traceId(MDC.get("traceId"))
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
                .traceId(MDC.get("traceId"))
                .success(false)
                .error("Authentication failed: " + ex.getMessage())
                .status(HttpStatus.UNAUTHORIZED.value())
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<DataResponse<Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, WebRequest request) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        DataResponse<Object> response = DataResponse.builder()
                .traceId(MDC.get("traceId"))
                .success(false)
                .error("Validation failed: " + errorMessage)
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
                .traceId(MDC.get("traceId"))
                .success(false)
                .error("Access denied: " + ex.getMessage())
                .status(HttpStatus.FORBIDDEN.value())
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }
}