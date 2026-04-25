package com.stilov.springboot_practice_2503.web;

import java.time.LocalDateTime;

public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime requestTime;

    private ApiResponse(boolean success, String message, T data, LocalDateTime time) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.requestTime = time;
    }

    private ApiResponse() {}

    public static <T> ApiResponse<T> responseOk(T data, String message){
            return new ApiResponse<T>(true, message, data, LocalDateTime.now());
    }

    public static ApiResponse<Void> responseOk(String message){
        return new ApiResponse<Void>(true, message, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> responseError(String message){
        return new ApiResponse<T>(false, message, null, LocalDateTime.now());
    }

    public T getData() {
        return data;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }
}
