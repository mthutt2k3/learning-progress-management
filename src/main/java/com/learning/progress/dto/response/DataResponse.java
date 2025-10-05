package com.learning.progress.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String error;

    private Integer status;
    private LocalDateTime timestamp;
    private String path;
    private String requestId;

    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
}
