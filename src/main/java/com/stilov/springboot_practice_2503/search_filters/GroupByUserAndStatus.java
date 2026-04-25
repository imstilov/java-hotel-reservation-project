package com.stilov.springboot_practice_2503.search_filters;

import com.stilov.springboot_practice_2503.reservations.ReservationStatus;

public record GroupByUserAndStatus(
        Long userId,
        ReservationStatus status,
        Integer pageSize,
        Integer pageNumber
) {
}
