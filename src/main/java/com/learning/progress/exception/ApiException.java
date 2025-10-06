package com.learning.progress.exception;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final Integer status;
    private final String errorCode;

    public ApiException(String message, Integer status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public ApiException(String message, Integer status) {
        this(message, status, null);
    }
}