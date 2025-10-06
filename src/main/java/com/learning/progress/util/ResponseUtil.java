package com.learning.progress.util;

import com.learning.progress.dto.response.DataResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

public class ResponseUtil {

    // Trả về success có data
    public static <T> ResponseEntity<DataResponse<T>> success(String message, T data) {
        return ResponseEntity.ok(
                DataResponse.<T>builder()
                        .success(true)
                        .message(message)
                        .status(HttpStatus.OK.value())
                        .data(data)
                        .traceId(TraceUtil.getTraceId())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    // Trả về success chỉ có message
    public static ResponseEntity<DataResponse<Void>> success(String message) {
        return ResponseEntity.ok(
                DataResponse.<Void>builder()
                        .success(true)
                        .message(message)
                        .status(HttpStatus.OK.value())
                        .traceId(TraceUtil.getTraceId())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    public static <T> ResponseEntity<DataResponse<T>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(DataResponse.<T>builder()
                        .success(false)
                        .message(message)
                        .status(status.value())
                        .traceId(TraceUtil.getTraceId())
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}

