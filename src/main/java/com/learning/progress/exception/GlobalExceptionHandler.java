package com.learning.progress.exception;

import com.learning.progress.dto.response.DataResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;

@Hidden
@ControllerAdvice
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
}