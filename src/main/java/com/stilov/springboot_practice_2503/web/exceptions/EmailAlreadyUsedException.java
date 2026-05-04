package com.stilov.springboot_practice_2503.web.exceptions;

public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String message) {
        super(message);
    }
}
