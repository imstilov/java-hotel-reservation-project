package com.stilov.springboot_practice_2503.web.exceptions;

public class RoomNotAvailableException extends ReservationException {
    private final Long roomId;

    public RoomNotAvailableException(Long roomId) {
        super("Room " + roomId + " is not available");
        this.roomId = roomId;
    }

    public Long getRoomId() { return roomId; }
}
