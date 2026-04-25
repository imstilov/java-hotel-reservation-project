package com.stilov.springboot_practice_2503.web.exceptions;

import com.stilov.springboot_practice_2503.reservations.ReservationStatus;

public class InvalidReservationStatusException extends ReservationException {

    public InvalidReservationStatusException(String message) {
        super(message);
    }

}
