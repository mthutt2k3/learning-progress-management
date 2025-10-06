package com.learning.progress.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataResponse<T> {

    private String traceId;
    private boolean success;
    private String message;
    private T data;

    private Integer status;
    private LocalDateTime timestamp;
    private String path;
    private String requestId;

    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;

    public static <T> DataResponse<T> success(T data, String message) {
        return DataResponse.<T>builder()
                .traceId(MDC.get("traceId")) // Lấy traceId từ MDC
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> DataResponse<T> error(String error, Integer status) {
        return DataResponse.<T>builder()
                .traceId(MDC.get("traceId")) // Lấy traceId từ MDC
                .success(false)
                .error(error)
                .status(status)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
