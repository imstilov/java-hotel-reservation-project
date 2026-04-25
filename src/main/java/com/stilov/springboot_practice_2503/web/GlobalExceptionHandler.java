package com.stilov.springboot_practice_2503.web;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        log.error("Handle exception", e);

        var errorResponse = ApiResponse.<Void>responseError(
                "Internal server error",
                e.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(EntityNotFoundException e) {
        log.error("Handle EntityNotFoundException", e);

        var errorResponse = ApiResponse.<Void>responseError(
                "Entity not found",
                e.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }

    @ExceptionHandler(exception = { IllegalArgumentException.class,
                                    IllegalStateException.class,
                                    MethodArgumentNotValidException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException e) {
        log.error("HandleBadRequest", e);

        var errorResponse = ApiResponse.<Void>responseError(
                "Bad request",
                e.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }
}
