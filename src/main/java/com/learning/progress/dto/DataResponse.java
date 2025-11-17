package com.learning.progress.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

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
    private String error;

    private OffsetDateTime startDate;
    private OffsetDateTime endDate;

    private Integer status;
    private LocalDateTime timestamp;
    private String path;
    private String requestId;

    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;

    // =====================================================================
    // STATIC SUCCESS / ERROR
    // =====================================================================

    public static <T> DataResponse<T> success(T data, String message) {
        return DataResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build()
                .withCurrentTraceId()
                .withCurrentTimestamp();
    }

    public static <T> DataResponse<T> error(String error, Integer status) {
        return DataResponse.<T>builder()
                .success(false)
                .error(error)
                .status(status)
                .build()
                .withCurrentTraceId()
                .withCurrentTimestamp();
    }

    // =====================================================================
    // CHAINABLE SETTERS (để dùng .page(page).size(size)...)
    // =====================================================================

    public DataResponse<T> page(Integer page) {
        this.page = page;
        return this;
    }

    public DataResponse<T> size(Integer size) {
        this.size = size;
        return this;
    }

    public DataResponse<T> totalElements(int totalElements) {
        this.totalElements = Long.valueOf(totalElements);
        return this;
    }
    public DataResponse<T> totalElements(Long totalElements) {
        this.totalElements = totalElements;
        return this;
    }

    public DataResponse<T> totalPages(Integer totalPages) {
        this.totalPages = totalPages;
        return this;
    }

    public DataResponse<T> status(Integer status) {
        this.status = status;
        return this;
    }

    public DataResponse<T> path(String path) {
        this.path = path;
        return this;
    }

    public DataResponse<T> requestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    // =====================================================================
    // HELPER: TỰ ĐỘNG GÁN traceId + timestamp
    // =====================================================================

    public DataResponse<T> withCurrentTraceId() {
        String current = MDC.get("traceId");
        if (current != null) this.traceId = current;
        return this;
    }

    public DataResponse<T> withCurrentTimestamp() {
        this.timestamp = LocalDateTime.now();
        return this;
    }


    // =====================================================================
    // BUILDER: TỰ ĐỘNG GÁN KHI BUILD
    // =====================================================================

    public static class DataResponseBuilder<T> {
        public DataResponse<T> build() {
            DataResponse<T> response = new DataResponse<>(
                    traceId, success, message, data, error,
                    startDate, endDate, status, timestamp, path, requestId,
                    page, size, totalElements, totalPages
            );
            return response.withCurrentTraceId().withCurrentTimestamp();
        }
    }
}