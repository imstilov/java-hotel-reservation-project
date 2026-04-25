package com.stilov.springboot_practice_2503.web.exceptions;

public class ReservationException extends RuntimeException{
    public ReservationException(String message) {
        super(message);
    }
}
