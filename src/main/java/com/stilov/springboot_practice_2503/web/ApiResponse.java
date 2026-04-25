package com.stilov.springboot_practice_2503.web;

import java.time.LocalDateTime;

public class ApiResponse<T> {
    private boolean success;
    private String message;
    private String detailedMessage;
    private T data;
    private LocalDateTime requestTime;

    private ApiResponse(boolean success, String message, String detailedMessage, T data, LocalDateTime time) {
        this.success = success;
        this.message = message;
        this.detailedMessage = detailedMessage;
        this.data = data;
        this.requestTime = time;
    }

    public static <T> ApiResponse<T> responseOk(T data, String message){
            return new ApiResponse<T>(true, message, null, data, LocalDateTime.now());
    }

    public static ApiResponse<Void> responseOk(String message){
        return new ApiResponse<Void>(true, message, null, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> responseError(String message, String detailedMessage){
        return new ApiResponse<T>(false, message, detailedMessage, null, LocalDateTime.now());
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

    public String getDetailedMessage() {
        return detailedMessage;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }
}
